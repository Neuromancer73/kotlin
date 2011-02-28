package org.jetbrains.jet.lang.resolve;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.jet.lang.psi.*;
import org.jetbrains.jet.lang.types.*;

import java.util.*;

/**
 * @author abreslav
 */
public class TopDownAnalyzer {

    private final WritableBindingContext context = new WritableBindingContext();
    private final Map<JetClass, MutableClassDescriptor> classes = new LinkedHashMap<JetClass, MutableClassDescriptor>();
    private final Map<JetFunction, FunctionDescriptor> functions = new HashMap<JetFunction, FunctionDescriptor>();
    private final Map<JetDeclaration, WritableScope> declaringScopes = new HashMap<JetDeclaration, WritableScope>();
    private Map<JetExpression, Type> expressionTypes = new HashMap<JetExpression, Type>();
    private Map<JetReferenceExpression,DeclarationDescriptor> resolutionResults = new HashMap<JetReferenceExpression, DeclarationDescriptor>();

    public BindingContext process(@NotNull JetScope outerScope, @NotNull List<JetDeclaration> declarations) {
        WritableScope toplevelScope = new WritableScope(outerScope);
        collectTypeDeclarators(toplevelScope, declarations);
        resolveTypeDeclarations();
        collectBehaviorDeclarators(toplevelScope, declarations);
        resolveBehaviorDeclarationBodies();
        return new BindingContext() {

            @Override
            public NamespaceDescriptor getNamespaceDescriptor(JetNamespace declaration) {
                throw new UnsupportedOperationException(); // TODO
            }

            @Override
            public ClassDescriptor getClassDescriptor(JetClass declaration) {
                return classes.get(declaration);
            }

            @Override
            public FunctionDescriptor getFunctionDescriptor(JetFunction declaration) {
                return functions.get(declaration);
            }

            @Override
            public PropertyDescriptor getPropertyDescriptor(JetProperty declaration) {
                throw new UnsupportedOperationException(); // TODO
            }

            @Override
            public Type getExpressionType(JetExpression expression) {
                return expressionTypes.get(expression);
            }

            @Override
            public DeclarationDescriptor resolve(JetReferenceExpression referenceExpression) {
                return resolutionResults.get(referenceExpression);
            }
        };
    }

////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    private void collectTypeDeclarators(
            @NotNull final WritableScope declaringScope,
            List<JetDeclaration> declarations) {
        for (JetDeclaration declaration : declarations) {
            declaration.accept(new JetVisitor() {
                @Override
                public void visitClass(JetClass klass) {
                    WritableScope classScope = processClass(declaringScope, klass);
                    collectTypeDeclarators(classScope, klass.getDeclarations());
                }

                @Override
                public void visitTypedef(JetTypedef typedef) {
                    processTypeDef(typedef);
                }

                @Override
                public void visitDeclaration(JetDeclaration dcl) {
                    throw new UnsupportedOperationException(); // TODO
                }
            });
        }
    }

    private WritableScope processClass(@NotNull WritableScope declaringScope, JetClass klass) {
        MutableClassDescriptor mutableClassDescriptor = new MutableClassDescriptor(declaringScope);
        mutableClassDescriptor.setName(klass.getName());
        declaringScope.addClassDescriptor(mutableClassDescriptor);
        classes.put(klass, mutableClassDescriptor);
        declaringScopes.put(klass, declaringScope);
        return mutableClassDescriptor.getUnsubstitutedMemberScope();
    }

    private void processExtension(JetExtension extension) {
        throw new UnsupportedOperationException(); // TODO
    }

    private void processTypeDef(JetTypedef typedef) {
        throw new UnsupportedOperationException(); // TODO
    }

////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    private void resolveTypeDeclarations() {
        for (Map.Entry<JetClass, MutableClassDescriptor> entry : classes.entrySet()) {
            JetClass jetClass = entry.getKey();
            MutableClassDescriptor descriptor = entry.getValue();
            ClassDescriptorResolver.INSTANCE.resolveMutableClassDescriptor(declaringScopes.get(jetClass), jetClass, descriptor);
        }
    }

////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    private void collectBehaviorDeclarators(@NotNull final WritableScope declaringScope, List<JetDeclaration> declarations) {
        for (JetDeclaration declaration : declarations) {
            declaration.accept(new JetVisitor() {
                @Override
                public void visitClass(JetClass klass) {
                    collectBehaviorDeclarators(classes.get(klass).getUnsubstitutedMemberScope(), klass.getDeclarations());
                }

                @Override
                public void visitClassObject(JetClassObject classObject) {
                    processClassObject(classObject);
                    collectBehaviorDeclarators(declaringScope, classObject.getObject().getDeclarations());
                }

                @Override
                public void visitNamespace(JetNamespace namespace) {
                    collectBehaviorDeclarators(declaringScope, namespace.getDeclarations());
                }

                @Override
                public void visitFunction(JetFunction function) {
                    processFunction(declaringScope, function);
                }

                @Override
                public void visitProperty(JetProperty property) {
                    processProperty(declaringScope, property);
                }

                @Override
                public void visitDeclaration(JetDeclaration dcl) {
                    throw new UnsupportedOperationException(); // TODO
                }
            });
        }

    }

    private void processFunction(@NotNull WritableScope declaringScope, JetFunction function) {
        declaringScopes.put(function, declaringScope);
        FunctionDescriptor descriptor = ClassDescriptorResolver.INSTANCE.resolveFunctionDescriptor(declaringScope, function);
        declaringScope.addFunctionDescriptor(descriptor);
        functions.put(function, descriptor);
    }

    private void processProperty(JetScope declaringScope, JetProperty property) {
        throw new UnsupportedOperationException(); // TODO
    }

    private void processClassObject(JetClassObject classObject) {
        throw new UnsupportedOperationException(); // TODO
    }

////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    private void resolveBehaviorDeclarationBodies() {
        for (Map.Entry<JetFunction, FunctionDescriptor> entry : functions.entrySet()) {
            JetFunction function = entry.getKey();
            FunctionDescriptor descriptor = entry.getValue();

            WritableScope declaringScope = declaringScopes.get(function);
            assert declaringScope != null;

            WritableScope parameterScope = new WritableScope(declaringScope);
            for (TypeParameterDescriptor typeParameter : descriptor.getTypeParameters()) {
                parameterScope.addTypeParameterDescriptor(typeParameter);
            }
            for (ValueParameterDescriptor valueParameterDescriptor : descriptor.getUnsubstitutedValueParameters()) {
                parameterScope.addPropertyDescriptor(valueParameterDescriptor);
            }

            resolveExpression(parameterScope, function.getBodyExpression(), true);
        }
    }

    private void resolveExpression(@NotNull JetScope scope, JetExpression expression, boolean preferBlock) {
        new JetTypeChecker(new TypeCheckerTrace() {
            @Override
            public void recordExpressionType(JetExpression expression, Type type) {
                expressionTypes.put(expression, type);
            }

            @Override
            public void recordResolutionResult(JetReferenceExpression expression, DeclarationDescriptor descriptor) {
                resolutionResults.put(expression, descriptor);
            }
        }).getType(scope, expression, preferBlock);
    }

}
