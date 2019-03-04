package prophet4j.util;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import picocli.CommandLine.Option;
import prophet4j.meta.FeatureStruct.Feature;
import prophet4j.meta.FeatureStruct.FeatureManager;
import prophet4j.meta.FeatureStruct.ValueToFeatureMapTy;
import prophet4j.meta.FeatureType;
import prophet4j.meta.FeatureType.AtomicFeature;
import prophet4j.meta.FeatureType.JointType;
import prophet4j.meta.FeatureType.Position;
import prophet4j.meta.FeatureType.RepairFeature;
import prophet4j.meta.FeatureType.ValueFeature;
import prophet4j.meta.RepairStruct.Repair;
import prophet4j.meta.RepairType.RepairActionKind;
import spoon.reflect.code.CtArrayAccess;
import spoon.reflect.code.CtIf;
import spoon.reflect.code.CtLiteral;
import spoon.reflect.code.CtLocalVariable;
import spoon.reflect.code.CtStatement;
import spoon.reflect.code.CtStatementList;
import spoon.reflect.code.CtVariableAccess;
import spoon.reflect.declaration.CtClass;
import spoon.reflect.declaration.CtElement;
import spoon.reflect.declaration.CtField;
import spoon.reflect.declaration.CtMethod;
import spoon.reflect.declaration.CtParameter;
import spoon.reflect.reference.CtExecutableReference;
import spoon.reflect.visitor.filter.TypeFilter;

// this is the new implementation following the implementation of Prophet4C
// based on FeatureExtract.cpp, RepairGenerator.cpp
public class FeatureExtractor {

    // check these variables
    @Option(names = {"--disable-mod"}, description = "Disable Modification Features.")
    private boolean DisableModificationFeatures = false;
    @Option(names = {"--disable-sema-cross"}, description = "Disable Semantic Features, setting them all zero!")
    private boolean DisableSemanticCrossFeatures = false;
    @Option(names = {"--disable-sema-value"}, description = "Disable Semantic-Value Features, setting them all zero!")
    private boolean DisableSemanticValueFeatures = false;

    Map<String, CtElement> valueExprInfo = new HashMap<>();

    private EnumSet<RepairFeature> getRepairFeatures(Repair repair) {
        EnumSet<RepairFeature> repairFeatures = EnumSet.noneOf(RepairFeature.class);
        if (repair.kind == null) {
            // I used null in FeatureExtractorTest.java
            return repairFeatures;
        }
        switch (repair.kind) {
            case IfExitKind:
                // RepairFeature.INSERT_CONTROL_RF == AddControlRepair
                repairFeatures.add(RepairFeature.INSERT_CONTROL_RF);
                break;
            case GuardKind:
            case SpecialGuardKind:
                // RepairFeature.INSERT_GUARD_RF == GuardRepair
                repairFeatures.add(RepairFeature.INSERT_GUARD_RF);
                break;
            case AddInitKind:
            case AddAndReplaceKind:
                // RepairFeature.INSERT_STMT_RF == AddStmtRepair
                repairFeatures.add(RepairFeature.INSERT_STMT_RF);
                break;
            case TightenConditionKind:
            case LoosenConditionKind:
                // RepairFeature.REPLACE_COND_RF == CondRepair
                repairFeatures.add(RepairFeature.REPLACE_COND_RF);
                break;
            case ReplaceKind:
            case ReplaceStringKind:
                // RepairFeature.REPLACE_STMT_RF == ReplaceStmtRepair
                repairFeatures.add(RepairFeature.REPLACE_STMT_RF);
                break;
        }
        return repairFeatures;
    }

    private EnumSet<ValueFeature> getValueFeature(final String valueStr, final Repair repair, Map<String, CtElement> valueExprInfo) {
        EnumSet<ValueFeature> valueFeatures = EnumSet.noneOf(ValueFeature.class);
        if (repair.oldRExpr != null && repair.newRExpr != null) {
            String oldStr = repair.oldRExpr.toString();
            String newStr = repair.newRExpr.toString();
            if (valueStr.equals(newStr))
                valueFeatures.add(ValueFeature.MODIFIED_VF);
            if (oldStr.length() > 0 && newStr.length() > 0) {
                double ratio = ((double)oldStr.length()) / newStr.length();
                if (ratio > 0.5 && ratio < 2 && oldStr.length() > 3 && newStr.length() > 3)
                    if (oldStr.contains(newStr) || newStr.contains(oldStr))
                        valueFeatures.add(ValueFeature.MODIFIED_SIMILAR_VF);
            }
        }
        CtElement element = repair.actions.get(0).dstElem;
        CtMethod FD = element.getParent(new TypeFilter<>(CtMethod.class));
        if (FD != null) {
            for (Object it: FD.getParameters()) {
                if (it instanceof CtParameter) {
                    CtParameter VD = (CtParameter) it;
                    if (VD.getSimpleName().equals(valueStr))
                        valueFeatures.add(ValueFeature.FUNC_ARGUMENT_VF);
                }
            }
        }
        if (valueStr.contains("length") || valueStr.contains("size"))
            valueFeatures.add(ValueFeature.SIZE_LITERAL_VF);
        assert(valueExprInfo.containsKey(valueStr));
//        CtStatement E = stripParenAndCast(valueExprInfo.get(v_str));
        CtElement E = valueExprInfo.get(valueStr);
        if (E instanceof CtVariableAccess || E instanceof CtArrayAccess || E instanceof CtLocalVariable) {
            if (E instanceof CtLocalVariable) {
                valueFeatures.add(ValueFeature.LOCAL_VARIABLE_VF);
            } else {
                valueFeatures.add(ValueFeature.GLOBAL_VARIABLE_VF);
            }
        } else if (E instanceof CtExecutableReference){
            // fixme: ...
            // to make CALLEE_AF be meaningful
            if (((CtExecutableReference) E).getParameters().size() > 0){
                valueFeatures.add(ValueFeature.LOCAL_VARIABLE_VF);
            }
        } else if (E instanceof CtIf){
            // fixme: ...
            // to make R_STMT_COND_AF be meaningful
            valueFeatures.add(ValueFeature.LOCAL_VARIABLE_VF);
        }
//        if (E instanceof CtVariable) {
//            if (E instanceof CtLocalVariable)
//                valueFeatures.add(ValueFeature.LOCAL_VARIABLE_VF);
//            else
//                valueFeatures.add(ValueFeature.GLOBAL_VARIABLE_VF);
//        } else if (E instanceof CtVariableReference) {
//            if (E instanceof CtLocalVariableReference)
//                valueFeatures.add(ValueFeature.LOCAL_VARIABLE_VF);
//            else
//                valueFeatures.add(ValueFeature.GLOBAL_VARIABLE_VF);
//        }
        // fixme: i feel this may be incorrect
        if (E.getElements(new TypeFilter<>(CtField.class)).size() > 0)
            valueFeatures.add(ValueFeature.MEMBER_VF);
        if (E instanceof CtLiteral) {
            Object value = ((CtLiteral)E).getValue();
            if (value instanceof String) {
                valueFeatures.add(ValueFeature.STRING_LITERAL_VF);
            } else if (value instanceof Integer) { // ?
                if ((Integer) value == 0) {
                    valueFeatures.add(ValueFeature.ZERO_CONST_VF);
                } else {
                    valueFeatures.add(ValueFeature.NONZERO_CONST_VF);
                }
            }
        }
        return valueFeatures;
    }

    private List<CtElement> getStmtList(CtElement statement) {
        List<CtElement> stmtList = new ArrayList<>();
        if (statement instanceof CtClass || statement instanceof CtMethod) {
            stmtList.add(statement);
        } else {
            CtElement parent = statement.getParent();
            List<CtStatement> tmpList = parent.getElements(new TypeFilter<>(CtStatement.class));
            if (parent instanceof CtStatement) {
                tmpList.remove(0);
            }
            stmtList.addAll(tmpList);
        }
        return stmtList;
    }

    private int getPivot(List<CtElement> srcStmtList, List<CtElement> dstStmtList) {
        int pivot = Math.min(srcStmtList.size(), dstStmtList.size());
        for (int i = 0; i < Math.min(srcStmtList.size(), dstStmtList.size()); i++) {
            if (!srcStmtList.get(i).equals(dstStmtList.get(i))) {
                pivot = i;
                break;
            }
        }
        return pivot;
    }

    // this is for CodeDiffer.java
    public FeatureManager extractFeature(Repair repair, CtElement atom) {
        // getImmediateFollowStmts() getLocalStmts()
//        List<CtStatement> srcStmtList = getStmtList((CtStatement)diffEntry.srcNode);
//        List<CtStatement> dstStmtList = getStmtList((CtStatement)diffEntry.dstNode);
//        int pivot = getPivot(srcStmtList, dstStmtList);

        FeatureManager featureManager = new FeatureManager();
        ValueToFeatureMapTy resv = new ValueToFeatureMapTy();
        ValueToFeatureMapTy resv_loc = new ValueToFeatureMapTy();
        ValueToFeatureMapTy resv_loc1 = new ValueToFeatureMapTy();
        ValueToFeatureMapTy resv_loc2 = new ValueToFeatureMapTy();

        // RepairFeatureNum     = RepairFeatureNum                      = 5
        EnumSet<RepairFeature> repairFeatures = getRepairFeatures(repair);
        // ModKind should be synonyms of RepairType
        for (FeatureType repairFeature : repairFeatures) {
            // RF_JT
            List<FeatureType> features = new ArrayList<>();
            features.add(repairFeature);
            featureManager.addFeature(new Feature(JointType.RF_JT, features));
        }

        FeatureVisitor FEV = new FeatureVisitor(valueExprInfo);
        FEV.traverseRepair(repair, atom);
        resv = FEV.getFeatureResult();
        List<CtElement> loc_stmts = getImmediateFollowStmts(repair);
        List<CtElement> loc1_stmts = new ArrayList<>();
        List<CtElement> loc2_stmts = new ArrayList<>();
        getLocalStmts(repair, loc1_stmts, loc2_stmts);
        resv_loc.map.clear();
        resv_loc1.map.clear();
        resv_loc2.map.clear();
        for (CtElement it : loc_stmts) {
            FEV = new FeatureVisitor(valueExprInfo);
            FEV.traverseStmt(it);
            ValueToFeatureMapTy resM = FEV.getFeatureResult();
            orMap(resv_loc, resM);
        }
        for (CtElement it : loc1_stmts) {
            FEV = new FeatureVisitor(valueExprInfo);
            FEV.traverseStmt(it);
            ValueToFeatureMapTy resM = FEV.getFeatureResult();
            orMap(resv_loc1, resM);
        }
        for (CtElement it : loc2_stmts) {
            FEV = new FeatureVisitor(valueExprInfo);
            FEV.traverseStmt(it);
            ValueToFeatureMapTy resM = FEV.getFeatureResult();
            orMap(resv_loc2, resM);
        }

        // GlobalFeatureNum     = 3 * AtomFeatureNum * RepairFeatureNum = 450
        for (FeatureType repairFeature : repairFeatures) {
            if (resv_loc.map.containsKey("")) {
                Set<AtomicFeature> atomicFeatures = resv_loc.map.get("");
                for (FeatureType atomicFeature : atomicFeatures) {
                    // POS_AF_RF_JT
                    List<FeatureType> globalFeatures = new ArrayList<>();
                    globalFeatures.add(Position.POS_C);
                    globalFeatures.add(atomicFeature);
                    globalFeatures.add(repairFeature);
                    featureManager.addFeature(new Feature(JointType.POS_AF_RF_JT, globalFeatures));
                }
            }
            if (resv_loc1.map.containsKey("")) {
                Set<AtomicFeature> atomicFeatures = resv_loc1.map.get("");
                for (FeatureType atomicFeature : atomicFeatures) {
                    // POS_AF_RF_JT
                    List<FeatureType> globalFeatures = new ArrayList<>();
                    globalFeatures.add(Position.POS_P);
                    globalFeatures.add(atomicFeature);
                    globalFeatures.add(repairFeature);
                    featureManager.addFeature(new Feature(JointType.POS_AF_RF_JT, globalFeatures));
                }
            }
            if (resv_loc2.map.containsKey("")) {
                Set<AtomicFeature> atomicFeatures = resv_loc2.map.get("");
                for (FeatureType atomicFeature : atomicFeatures) {
                    // POS_AF_RF_JT
                    List<FeatureType> globalFeatures = new ArrayList<>();
                    globalFeatures.add(Position.POS_N);
                    globalFeatures.add(atomicFeature);
                    globalFeatures.add(repairFeature);
                    featureManager.addFeature(new Feature(JointType.POS_AF_RF_JT, globalFeatures));
                }
            }
        }
        resv.map.remove("");
        resv_loc.map.remove("");
        resv_loc1.map.remove("");
        resv_loc2.map.remove("");

        // VarCrossFeatureNum   = 3 * AtomFeatureNum * AtomFeatureNum   = 2700
        for (String key : resv.map.keySet()) {
            if (valueExprInfo.containsKey(key)) {
                CtElement E = valueExprInfo.get(key);
                if (E instanceof CtLiteral)
                    if (((CtLiteral)E).getValue() instanceof Integer) // ?
                        continue;
            }
            if (resv_loc.map.containsKey(key)) {
                Set<AtomicFeature> dstAtomicFeatures = resv.map.get(key);
                Set<AtomicFeature> srcAtomicFeatures = resv_loc.map.get(key);
                for (FeatureType dstAtomicFeature : dstAtomicFeatures) {
                    for (FeatureType srcAtomicFeature : srcAtomicFeatures) {
                        // POS_AF_AF_JT
                        List<FeatureType> varCrossFeatures = new ArrayList<>();
                        varCrossFeatures.add(Position.POS_C);
                        varCrossFeatures.add(srcAtomicFeature);
                        varCrossFeatures.add(dstAtomicFeature);
                        featureManager.addFeature(new Feature(JointType.POS_AF_AF_JT, varCrossFeatures));
                    }
                }
            }
            if (resv_loc1.map.containsKey(key)) {
                Set<AtomicFeature> dstAtomicFeatures = resv.map.get(key);
                Set<AtomicFeature> srcAtomicFeatures = resv_loc1.map.get(key);
                for (FeatureType dstAtomicFeature : dstAtomicFeatures) {
                    for (FeatureType srcAtomicFeature : srcAtomicFeatures) {
                        // POS_AF_AF_JT
                        List<FeatureType> varCrossFeatures = new ArrayList<>();
                        varCrossFeatures.add(Position.POS_P);
                        varCrossFeatures.add(srcAtomicFeature);
                        varCrossFeatures.add(dstAtomicFeature);
                        featureManager.addFeature(new Feature(JointType.POS_AF_AF_JT, varCrossFeatures));
                    }
                }
            }
            if (resv_loc2.map.containsKey(key)) {
                Set<AtomicFeature> dstAtomicFeatures = resv.map.get(key);
                Set<AtomicFeature> srcAtomicFeatures = resv_loc2.map.get(key);
                for (FeatureType dstAtomicFeature : dstAtomicFeatures) {
                    for (FeatureType srcAtomicFeature : srcAtomicFeatures) {
                        // POS_AF_AF_JT
                        List<FeatureType> varCrossFeatures = new ArrayList<>();
                        varCrossFeatures.add(Position.POS_N);
                        varCrossFeatures.add(srcAtomicFeature);
                        varCrossFeatures.add(dstAtomicFeature);
                        featureManager.addFeature(new Feature(JointType.POS_AF_AF_JT, varCrossFeatures));
                    }
                }
            }
        }

        // ValueCrossFeatureNum = AtomFeatureNum * ValueFeatureNum      = 360
        for (String key : resv.map.keySet()) {
            Set<AtomicFeature> atomicFeatures = resv.map.get(key);
            Set<ValueFeature> valueFeatures = getValueFeature(key, repair, valueExprInfo);
            for (FeatureType atomicFeature : atomicFeatures) {
                for (FeatureType valueFeature : valueFeatures) {
                    // AF_VF_JT
                    List<FeatureType> valueCrossFeature = new ArrayList<>();
                    valueCrossFeature.add(atomicFeature);
                    valueCrossFeature.add(valueFeature);
                    featureManager.addFeature(new Feature(JointType.AF_VF_JT, valueCrossFeature));
                }
            }
        }
        return featureManager;
    }

    private List<CtElement> getImmediateFollowStmts(Repair repair) {
        List<CtElement> ret = new ArrayList<>();
        CtElement srcElem = repair.actions.get(0).srcElem;
        if (repair.actions.get(0).kind != RepairActionKind.ReplaceMutationKind) {
            ret.add(srcElem);
            return ret;
        }
        else {
            ret.add(srcElem);
            CtStatement ElseB = null;
            if (repair.actions.get(0).dstElem instanceof CtIf) {
                CtIf IFS = (CtIf) repair.actions.get(0).dstElem;
                if (IFS.getThenStatement() instanceof CtStatementList) {
                    CtStatementList CS = IFS.getThenStatement();
                    ret.addAll(CS.getStatements());
                }
                else
                    ret.add(IFS.getThenStatement());
                ElseB = IFS.getElseStatement();
                if (ElseB != null) {
                    if (ElseB instanceof CtStatementList) {
                        CtStatementList CS = IFS.getThenStatement();
                        ret.addAll(CS.getStatements());
                    }
                    else
                        ret.add(ElseB);
                }
            }
            if (ElseB==null) {
                if (srcElem.getParent() instanceof CtStatementList) {
                    CtStatementList CS = (CtStatementList) srcElem.getParent();
                    boolean found = false;
                    for (CtStatement it : CS.getStatements()) {
                        if (found) {
                            ret.add(it);
                            break;
                        }
                        if (it.equals(srcElem))
                            found = true;
                    }
                }
            }
            return ret;
        }
    }

    private void getLocalStmts(Repair repair, List<CtElement> ret_before, List<CtElement> ret_after) {
        final int LOOKUP_DIS = 3;
        ret_before.clear();
        ret_after.clear();
        CtElement srcElem = repair.actions.get(0).srcElem;
        // Grab all compound stmt that is around the original stmt
        if (srcElem.getParent() instanceof CtStatementList) {
            CtStatementList CS = (CtStatementList) srcElem.getParent();
            List<CtStatement> tmp = new ArrayList<>();
            int idx = 0;
            boolean found = false;
            for (CtStatement it: CS.getStatements()) {
                if (it.equals(srcElem)) {
                    found = true;
                    idx = tmp.size();
                }
                tmp.add(it);
            }
            assert(found);
            int s = 0;
            if (idx > LOOKUP_DIS)
                s = idx - LOOKUP_DIS;
            int e = tmp.size();
            if (idx + LOOKUP_DIS + 1 < tmp.size())
                e = idx + LOOKUP_DIS + 1;
            boolean before = true;
            for (int i = s; i < e; i++) {
                if (tmp.get(i).equals(srcElem)) {
                    if (before)
                        ret_before.add(tmp.get(i));
                    else
                        ret_after.add(tmp.get(i));
                }
                if (tmp.get(i).equals(srcElem))
                    before = false;
            }
        }
        if (repair.actions.get(0).kind != RepairActionKind.ReplaceMutationKind)
            ret_after.add(srcElem);
    }

    private void orMap(ValueToFeatureMapTy m1, final ValueToFeatureMapTy m2) {
        for (String k: m2.map.keySet()) {
            if(!m1.map.containsKey(k)) {
                m1.map.put(k, m2.map.get(k));
            } else {
                m1.map.get(k).addAll(m2.map.get(k));
            }
        }
    }
}