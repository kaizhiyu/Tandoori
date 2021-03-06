package tandoori.analyzer;

import java.util.Arrays;
import java.util.List;

import spoon.reflect.code.BinaryOperatorKind;
import spoon.reflect.code.CtAssignment;
import spoon.reflect.code.CtBinaryOperator;
import spoon.reflect.code.CtBreak;
import spoon.reflect.code.CtCase;
import spoon.reflect.code.CtCatch;
import spoon.reflect.code.CtConditional;
import spoon.reflect.code.CtContinue;
import spoon.reflect.code.CtFieldAccess;
import spoon.reflect.code.CtFieldRead;
import spoon.reflect.code.CtFieldWrite;
import spoon.reflect.code.CtIf;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.code.CtLocalVariable;
import spoon.reflect.code.CtLoop;
import spoon.reflect.code.CtReturn;
import spoon.reflect.code.CtStatement;
import spoon.reflect.code.CtThisAccess;
import spoon.reflect.code.CtThrow;
import spoon.reflect.code.CtVariableRead;
import spoon.reflect.declaration.CtMethod;
import spoon.reflect.declaration.CtParameter;
import spoon.reflect.declaration.CtType;
import spoon.reflect.declaration.ModifierKind;
import spoon.reflect.visitor.filter.TypeFilter;
import tandoori.entities.PaprikaArgument;
import tandoori.entities.PaprikaMethod;
import tandoori.entities.PaprikaModifiers;

/**
 * Created by sarra on 20/02/17.
 */
public class MethodProcessor {
    public void process(CtMethod ctMethod) {

        String name = ctMethod.getSimpleName();
        String returnType = ctMethod.getType().getQualifiedName();

        String visibility = ctMethod.getVisibility() == null ? "null" : ctMethod.getVisibility().toString();
        PaprikaModifiers paprikaModifiers = DataConverter.convertTextToModifier(visibility);
        if (paprikaModifiers == null) {
            paprikaModifiers = PaprikaModifiers.PROTECTED;
        }
        int position = 0;
        String qualifiedName;
        PaprikaMethod paprikaMethod = PaprikaMethod.createPaprikaMethod(name, paprikaModifiers, returnType,
                MainProcessor.currentClass);
        MainProcessor.currentMethod = paprikaMethod;
        for (CtParameter<?> ctParameter : (List<CtParameter>) ctMethod.getParameters()) {
            qualifiedName = ctParameter.getType().getQualifiedName();
            PaprikaArgument.createPaprikaArgument(qualifiedName, position, paprikaMethod);
            position++;
        }
        int numberOfDeclaredLocals = ctMethod.getElements(new TypeFilter<CtLocalVariable>(CtLocalVariable.class)).size();
        paprikaMethod.setNumberOfLines(countEffectiveCodeLines(ctMethod));
        handleUsedVariables(ctMethod, paprikaMethod);
        handleInvocations(ctMethod, paprikaMethod);
        paprikaMethod.setComplexity(getComplexity(ctMethod));
        paprikaMethod.setNumberOfDeclaredLocals(numberOfDeclaredLocals);
        paprikaMethod.setSetter(checkSetter(ctMethod));
        paprikaMethod.setGetter(checkGetter(ctMethod));
        for (ModifierKind modifierKind : ctMethod.getModifiers()) {
            if (modifierKind.toString().toLowerCase().equals("static")) {
                paprikaMethod.setStatic(true);
                break;
            }
        }
    }

    private int countEffectiveCodeLines(CtMethod ctMethod) {
        try {
            return ctMethod.getBody().toString().split("\n").length;
        }catch (NullPointerException npe){
            return ctMethod.getPosition().getEndLine()-ctMethod.getPosition().getLine();
        }

    }

    private void handleUsedVariables(CtMethod ctMethod, PaprikaMethod paprikaMethod) {
        List<CtFieldAccess> elements = ctMethod.getElements(new TypeFilter<CtFieldAccess>(CtFieldAccess.class));
        String variableTarget = null;
        String variableName;


        for (CtFieldAccess ctFieldAccess : elements) {
            if (ctFieldAccess.getTarget() != null && ctFieldAccess.getTarget().getType() != null) {
                if(ctFieldAccess.getTarget().getType().getDeclaration() == ctMethod.getDeclaringType()){
                    variableTarget = ctFieldAccess.getTarget().getType().getQualifiedName();
                    variableName = ctFieldAccess.getVariable().getSimpleName();
                    paprikaMethod.getUsedVariablesData().add(new VariableData(variableTarget, variableName));
                }

            }


        }

    }

    private void handleInvocations(CtMethod ctMethod, PaprikaMethod paprikaMethod) {
        String targetName;
        String executable;
        String type="Uknown";
        List<CtInvocation> invocations = ctMethod.getElements(new TypeFilter<CtInvocation>(CtInvocation.class));
        for (CtInvocation invocation : invocations) {
            targetName = getTarget(invocation);
            executable = invocation.getExecutable().getSimpleName();
            if(invocation.getExecutable().getType()!=null){
                type=invocation.getExecutable().getType().getQualifiedName();
            }
            if (targetName != null) {
                paprikaMethod.getInvocationData().add(new InvocationData(targetName, executable,type));
            }
        }
    }

    private String getTarget(CtInvocation ctInvocation) {
        if (ctInvocation.getTarget() instanceof CtInvocation) {
            return getTarget((CtInvocation) ctInvocation.getTarget());
        } else {
            try {
                return ctInvocation.getExecutable().getDeclaringType().getQualifiedName();
            } catch (NullPointerException nullPointerException) {
                System.out.println("Error message : "+nullPointerException.getLocalizedMessage());
            }
        }
        return null;
    }

    private int getComplexity(CtMethod<?> ctMethod) {
        int numberOfTernaries = ctMethod.getElements(new TypeFilter<CtConditional>(CtConditional.class)).size();
        int numberOfIfs = ctMethod.getElements(new TypeFilter<CtIf>(CtIf.class)).size();
        int numberOfCases = ctMethod.getElements(new TypeFilter<CtCase>(CtCase.class)).size();
        int numberOfReturns = ctMethod.getElements(new TypeFilter<CtReturn>(CtReturn.class)).size();
        int numberOfLoops = ctMethod.getElements(new TypeFilter<CtLoop>(CtLoop.class)).size();
        int numberOfBinaryOperators = ctMethod.getElements(new TypeFilter<CtBinaryOperator>(CtBinaryOperator.class) {
            private final List<BinaryOperatorKind> operators = Arrays.asList(BinaryOperatorKind.AND, BinaryOperatorKind.OR);

            @Override
            public boolean matches(CtBinaryOperator element) {
                return super.matches(element) && operators.contains(element.getKind());
            }
        }).size();
        int numberOfCatches = ctMethod.getElements(new TypeFilter<CtCatch>(CtCatch.class)).size();
        int numberOfThrows = ctMethod.getElements(new TypeFilter<CtThrow>(CtThrow.class)).size();
        int numberOfBreaks = ctMethod.getElements(new TypeFilter<CtBreak>(CtBreak.class)).size();
        int numberOfContinues = ctMethod.getElements(new TypeFilter<CtContinue>(CtContinue.class)).size();
        return numberOfBreaks + numberOfCases + numberOfCatches + numberOfContinues + numberOfIfs + numberOfLoops +
                numberOfReturns + numberOfTernaries + numberOfThrows + numberOfBinaryOperators + 1;
    }

    private boolean checkGetter(CtMethod element) {
        if(element.getBody()==null){
            return false;
        }
        if (element.getBody().getStatements().size() != 1) {
            return false;
        }
        CtStatement statement = element.getBody().getStatement(0);
        if (!(statement instanceof CtReturn)) {
            return false;
        }

        CtReturn retur = (CtReturn) statement;
        if (!(retur.getReturnedExpression() instanceof CtFieldRead)) {
            return false;
        }
        CtFieldRead returnedExpression = (CtFieldRead) retur.getReturnedExpression();

        CtType parent = element.getParent(CtType.class);
        if(parent == null){
            return false;
        }
        try {
            if (parent.equals(returnedExpression.getVariable().getDeclaration().getDeclaringType())) {
                return true;
            }
        }catch (NullPointerException npe){
            System.out.println(npe.getLocalizedMessage());
        }
        return false;

    }

    private boolean checkSetter(CtMethod element) {
        if(element.getBody()==null){
            return false;
        }
        if (element.getBody().getStatements().size() != 1) {
            return false;
        }
        if (element.getParameters().size() != 1) {
            return false;
        }
        CtStatement statement = element.getBody().getStatement(0);
        // the last statement is an assignment
        if (!(statement instanceof CtAssignment)) {
            return false;
        }

        CtAssignment ctAssignment = (CtAssignment) statement;
        if (!(ctAssignment.getAssigned() instanceof CtFieldWrite)) {
            return false;
        }
        if (!(ctAssignment.getAssignment() instanceof CtVariableRead)) {
            return false;
        }
        CtVariableRead ctVariableRead = (CtVariableRead) ctAssignment.getAssignment();
        if (element.getParameters().size() != 1) {
            return false;
        }
        if (!(ctVariableRead.getVariable().getDeclaration().equals(element.getParameters().get(0)))) {
            return false;
        }
        CtFieldWrite returnedExpression = (CtFieldWrite) ((CtAssignment) statement).getAssigned();
        if (returnedExpression.getTarget() instanceof CtThisAccess) {
            return true;
        }

        return false;
    }
}
