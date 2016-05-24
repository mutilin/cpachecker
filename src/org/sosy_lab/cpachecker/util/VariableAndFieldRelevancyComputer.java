/*
 *  CPAchecker is a tool for configurable software verification.
 *  This file is part of CPAchecker.
 *
 *  Copyright (C) 2007-2016  Dirk Beyer
 *  All rights reserved.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 *
 *  CPAchecker web page:
 *    http://cpachecker.sosy-lab.org
 */
package org.sosy_lab.cpachecker.util;

import com.google.common.base.Optional;
import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.LinkedHashMultimap;
import com.google.common.collect.Multimap;

import org.sosy_lab.common.collect.PathCopyingPersistentTreeMap;
import org.sosy_lab.common.collect.PersistentLinkedList;
import org.sosy_lab.common.collect.PersistentList;
import org.sosy_lab.common.collect.PersistentSortedMap;
import org.sosy_lab.cpachecker.cfa.CFA;
import org.sosy_lab.cpachecker.cfa.ast.c.CArraySubscriptExpression;
import org.sosy_lab.cpachecker.cfa.ast.c.CAssignment;
import org.sosy_lab.cpachecker.cfa.ast.c.CBinaryExpression;
import org.sosy_lab.cpachecker.cfa.ast.c.CCastExpression;
import org.sosy_lab.cpachecker.cfa.ast.c.CComplexCastExpression;
import org.sosy_lab.cpachecker.cfa.ast.c.CDeclaration;
import org.sosy_lab.cpachecker.cfa.ast.c.CExpression;
import org.sosy_lab.cpachecker.cfa.ast.c.CExpressionAssignmentStatement;
import org.sosy_lab.cpachecker.cfa.ast.c.CFieldReference;
import org.sosy_lab.cpachecker.cfa.ast.c.CFunctionCallExpression;
import org.sosy_lab.cpachecker.cfa.ast.c.CFunctionCallStatement;
import org.sosy_lab.cpachecker.cfa.ast.c.CIdExpression;
import org.sosy_lab.cpachecker.cfa.ast.c.CInitializers;
import org.sosy_lab.cpachecker.cfa.ast.c.CPointerExpression;
import org.sosy_lab.cpachecker.cfa.ast.c.CRightHandSide;
import org.sosy_lab.cpachecker.cfa.ast.c.CRightHandSideVisitor;
import org.sosy_lab.cpachecker.cfa.ast.c.CStatement;
import org.sosy_lab.cpachecker.cfa.ast.c.CUnaryExpression;
import org.sosy_lab.cpachecker.cfa.ast.c.CUnaryExpression.UnaryOperator;
import org.sosy_lab.cpachecker.cfa.ast.c.CVariableDeclaration;
import org.sosy_lab.cpachecker.cfa.ast.c.DefaultCExpressionVisitor;
import org.sosy_lab.cpachecker.cfa.model.CFAEdge;
import org.sosy_lab.cpachecker.cfa.model.c.CAssumeEdge;
import org.sosy_lab.cpachecker.cfa.model.c.CDeclarationEdge;
import org.sosy_lab.cpachecker.cfa.model.c.CFunctionCallEdge;
import org.sosy_lab.cpachecker.cfa.model.c.CFunctionReturnEdge;
import org.sosy_lab.cpachecker.cfa.model.c.CReturnStatementEdge;
import org.sosy_lab.cpachecker.cfa.model.c.CStatementEdge;
import org.sosy_lab.cpachecker.cfa.types.c.CCompositeType;
import org.sosy_lab.cpachecker.cfa.types.c.CPointerType;
import org.sosy_lab.cpachecker.cfa.types.c.CType;
import org.sosy_lab.cpachecker.exceptions.UnrecognizedCCodeException;
import org.sosy_lab.cpachecker.util.VariableClassification.Partition;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Map;
import java.util.Queue;
import java.util.Set;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public final class VariableAndFieldRelevancyComputer {

  private static class VariableOrField implements Comparable<VariableOrField> {
    private static final class Unknown extends VariableOrField {
      private Unknown() {
      }

      @Override
      public String toString() {
        return "<Unknown>";
      }

      @Override
      public boolean equals(final Object o) {
        if (o == this) {
          return true;
        } else if (!(o instanceof Unknown)) {
          return false;
        } else {
          return true;
        }
      }

      @Override
      public int compareTo(final VariableOrField other) {
        if (this == other) {
          return 0;
        } else if (other instanceof Variable) {
          return -1;
        } else if (other instanceof Field) {
          return -1;
        } else {
          throw new AssertionError("Should not happen: all cases are covered above");
        }
      }

      @Override
      public int hashCode() {
        return 7;
      }

      private static final Unknown INSTANCE = new Unknown();
    }

    private static final class Variable extends VariableOrField {
      private Variable(final @Nonnull String scopedName) {
        this.scopedName = scopedName;
      }

      public @Nonnull String getScopedName() {
        return scopedName;
      }

      @Override
      public String toString() {
        return getScopedName();
      }

      @Override
      public boolean equals(final Object o) {
        if (o == this) {
          return true;
        } else if (!(o instanceof Variable)) {
          return false;
        } else {
          final Variable other = (Variable) o;
          return this.scopedName.equals(other.scopedName);
        }
      }

      @Override
      public int compareTo(final VariableOrField other) {
        if (this == other) {
          return 0;
        } else if (other instanceof Unknown) {
          return 1;
        } else if (other instanceof Field) {
          return -1;
        } else if (other instanceof Variable){
          return scopedName.compareTo(((Variable) other).scopedName);
        } else {
          throw new AssertionError("Should not happen: all cases are covered above");
        }
      }

      @Override
      public int hashCode() {
        return scopedName.hashCode();
      }

      private final @Nonnull String scopedName;
    }

    private static final class Field extends VariableOrField {
      private Field(final CCompositeType composite, final String name) {
        this.composite = composite;
        this.name = name;
      }

      public CCompositeType getCompositeType() {
        return composite;
      }

      public String getName() {
        return name;
      }

      @Override
      public String toString() {
        return composite + SCOPE_SEPARATOR + name;
      }

      @Override
      public boolean equals(final Object o) {
        if (o == this) {
          return true;
        } else if (!(o instanceof Field)) {
          return false;
        } else {
          final Field other = (Field) o;
          return this.composite.equals(other.composite) && this.name.equals(other.name);
        }
      }

      @Override
      public int compareTo(final VariableOrField other) {
        if (this == other) {
          return 0;
        } else if (other instanceof Unknown) {
          return 1;
        } else if (other instanceof Variable) {
          return 1;
        } else if (other instanceof Field) {
          final Field o = (Field) other;
          final int result = composite.getQualifiedName().compareTo(o.composite.getQualifiedName());
          return  result != 0 ? result :
                  name.compareTo(o.name);
        } else {
          throw new AssertionError("Should not happen: all cases are covered above");
        }
      }

      @Override
      public int hashCode() {
        final int prime = 67;
        return prime * composite.hashCode() + name.hashCode();
      }

      private final @Nonnull CCompositeType composite;
      private final @Nonnull String name;
    }

    private VariableOrField() {
    }

    public static @Nonnull Variable newVariable(final @Nonnull String scopedName) {
      return new Variable(scopedName);
    }

    public static @Nonnull Field newField(final @Nonnull CCompositeType composite, final @Nonnull String name) {
      return new Field(composite, name);
    }

    public static @Nonnull Unknown unknown() {
      return Unknown.INSTANCE;
    }

    public boolean isVariable() {
      return this instanceof Variable;
    }

    public boolean isField() {
      return this instanceof Field;
    }

    public boolean isUnknown() {
      return this instanceof Unknown;
    }

    public @Nonnull Variable asVariable() {
      if (this instanceof Variable) {
        return (Variable) this;
      } else {
        throw new ClassCastException("Tried to match " + this.getClass().getName() + " with " +
                                     Variable.class.getName());
      }
    }

    public @Nonnull Field asField() {
      if (this instanceof Field) {
        return (Field) this;
      } else {
        throw new ClassCastException("Tried to match " + this.getClass().getName() + " with " + Field.class.getName());
      }
    }

    @Override
    public int compareTo(final VariableOrField other) {
      throw new AssertionError("Should not happen: comparison should always be called on an object of a subclass");
    }
  }

  private static final class ComparableCompositeType implements Comparable<ComparableCompositeType> {
    private ComparableCompositeType(final CCompositeType type) {
      this.type = type;
    }

    @Override
    public int compareTo(final ComparableCompositeType other) {
      if (other == this) {
        return 0;
      } else {
        return type.getQualifiedName().compareTo(other.type.getQualifiedName());
      }
    }

    public static ComparableCompositeType of(final CCompositeType type) {
      return new ComparableCompositeType(type);
    }

    public CCompositeType compositeType() {
      return type;
    }

    private final CCompositeType type;
  }

  private static final class VarFieldDependencies {

    private VarFieldDependencies(PersistentSortedMap<String, Boolean> relevantVariables,
       PersistentSortedMap<ComparableCompositeType, PersistentList<String>> relevantFields,
       PersistentSortedMap<String, Boolean> addressedVariables,
       PersistentSortedMap<VariableOrField, PersistentList<VariableOrField>> dependencies,
       PersistentList<VarFieldDependencies> pendingMerges,
       final int size,
       final boolean forceSquash) {
         if (forceSquash || pendingMerges.size() > MAX_PENDING_MERGES) {
           for (VarFieldDependencies deps : pendingMerges) {
             for (final Map.Entry<String, Boolean> e : deps.relevantVariables.entrySet()) {
               relevantVariables = relevantVariables.putAndCopy(e.getKey(), e.getValue());
             }
             for (final Map.Entry<ComparableCompositeType, PersistentList<String>> e : deps.relevantFields.entrySet()) {
               relevantFields = relevantFields.putAndCopy(e.getKey(), relevantFields.get(e.getKey())
                                                                                    .withAll(e.getValue()));
             }
             for (final Map.Entry<String, Boolean> e : deps.addressedVariables.entrySet()) {
               addressedVariables = addressedVariables.putAndCopy(e.getKey(), e.getValue());
             }
             for (final Map.Entry<VariableOrField, PersistentList<VariableOrField>> e : deps.dependencies.entrySet()) {
               dependencies = dependencies.putAndCopy(e.getKey(), dependencies.get(e.getKey()).withAll(e.getValue()));
             }
           }
           pendingMerges = PersistentLinkedList.<VarFieldDependencies>of();
         }
         this.relevantVariables = relevantVariables;
         this.relevantFields = relevantFields;
         this.addressedVariables = addressedVariables;
         this.dependencies = dependencies;
         this.pendingMerges = pendingMerges;
         this.size = size;
     }

    private VarFieldDependencies(final PersistentSortedMap<String, Boolean> relevantVariables,
        final PersistentSortedMap<ComparableCompositeType, PersistentList<String>> relevantFields,
        final PersistentSortedMap<String, Boolean> addressedVariables,
        final PersistentSortedMap<VariableOrField, PersistentList<VariableOrField>> dependencies,
        final PersistentList<VarFieldDependencies> pendingMerges,
        final int size) {
        this(relevantVariables, relevantFields, addressedVariables, dependencies, pendingMerges, size, false);
    }

     public static VarFieldDependencies emptyDependencies() {
       return EMPTY_DEPENDENCIES;
     }

     public VarFieldDependencies withDependency(final @Nonnull VariableOrField lhs,
                                                final @Nonnull VariableOrField rhs) {
       if (!lhs.isUnknown()) {
         return new VarFieldDependencies(relevantVariables, relevantFields, addressedVariables,
                                         dependencies.putAndCopy(lhs, dependencies.get(lhs).with(rhs)),
                                         pendingMerges,
                                         size + 1);
       } else {
         if (rhs.isVariable()) {
           return new VarFieldDependencies(relevantVariables.putAndCopy(rhs.asVariable().getScopedName(),
                                                                        DUMMY_PRESENT),
                                           relevantFields, addressedVariables, dependencies, pendingMerges,
                                           size + 1);
         } else if (rhs.isField()) {
           final VariableOrField.Field field = rhs.asField();
           final ComparableCompositeType key = ComparableCompositeType.of(field.getCompositeType());
           return new VarFieldDependencies(relevantVariables,
                                           relevantFields.putAndCopy(key, relevantFields.get(key)
                                                                                        .with(field.getName())),
                                           addressedVariables, dependencies, pendingMerges,
                                           size + 1);
         } else if (rhs.isUnknown()) {
           throw new IllegalArgumentException("Can't handle dependency on Unknown");
         } else {
           throw new AssertionError("Should be unreachable: all possible cases already handled");
         }
       }
     }

     public VarFieldDependencies withAddressedVariable(final @Nonnull VariableOrField.Variable variable) {
       return new VarFieldDependencies(relevantVariables, relevantFields,
                                       addressedVariables.putAndCopy(variable.getScopedName(), DUMMY_PRESENT),
                                       dependencies, pendingMerges,
                                       size + 1);
     }

     public VarFieldDependencies withDependencies(final @Nonnull VarFieldDependencies other) {
       if (size >= other.size) {
         return new VarFieldDependencies(relevantVariables, relevantFields, addressedVariables, dependencies,
                                         pendingMerges.withAll(other.pendingMerges).with(other),
                                         size + other.size);
       } else {
         return new VarFieldDependencies(other.relevantVariables, other.relevantFields, other.addressedVariables,
                                         other.dependencies,
                                         other.pendingMerges.withAll(pendingMerges).with(this),
                                         size + other.size);
       }
     }

     private void ensureSquashed() {
       if (squashed == null) {
         squashed = new VarFieldDependencies(relevantVariables,
                                             relevantFields, addressedVariables, dependencies, pendingMerges,
                                             size, true);
       }
     }

     public ImmutableSet<String> computeAddressedVariables() {
       ensureSquashed();
       return ImmutableSet.copyOf(squashed.addressedVariables.keySet());
     }

     public Pair<ImmutableSet<String>, ImmutableMultimap<CCompositeType, String>> computeRelevantVariablesAndFields() {
       ensureSquashed();
       Queue<VariableOrField> queue = new ArrayDeque<>(relevantVariables.size() + relevantFields.size());
       Set<String> currentRelevantVariables = new HashSet<>();
       Multimap<CCompositeType, String> currentRelevantFields = LinkedHashMultimap.create();
       for (final String relevantVariable : relevantVariables.keySet()) {
         queue.add(VariableOrField.newVariable(relevantVariable));
         currentRelevantVariables.add(relevantVariable);
       }
       for (final Map.Entry<ComparableCompositeType, PersistentList<String>> relevantField :
            relevantFields.entrySet()) {
         for (final String s : relevantField.getValue()) {
           queue.add(VariableOrField.newField(relevantField.getKey().compositeType(), s));
           currentRelevantFields.put(relevantField.getKey().compositeType(), s);
         }
       }
       while (!queue.isEmpty()) {
         final VariableOrField relevantVariableOrField = queue.poll();
         final PersistentList<VariableOrField> variableOrFieldList = dependencies.get(relevantVariableOrField);
         if (variableOrFieldList != null) {
           for (VariableOrField variableOrField : variableOrFieldList) {
             assert variableOrField.isVariable() || variableOrField.isField() :
               "Match failure: neither variable nor field!";
             if (variableOrField.isVariable()) {
               final VariableOrField.Variable variable = variableOrField.asVariable();
               if (!currentRelevantVariables.contains(variable.getScopedName())) {
                 currentRelevantVariables.add(variable.getScopedName());
                 queue.add(variable);
               }
             } else { // Field
               final VariableOrField.Field field = variableOrField.asField();
               if (currentRelevantFields.containsEntry(field.getCompositeType(), field.getName())) {
                 currentRelevantFields.put(field.getCompositeType(), field.getName());
                 queue.add(field);
               }
             }
           }
         }
       }

       return Pair.of(ImmutableSet.copyOf(currentRelevantVariables), ImmutableMultimap.copyOf(currentRelevantFields));
     }

     private final PersistentSortedMap<String, Boolean> relevantVariables;
     private final PersistentSortedMap<ComparableCompositeType, PersistentList<String>> relevantFields;
     private final PersistentSortedMap<String, Boolean> addressedVariables;
     private final PersistentSortedMap<VariableOrField, PersistentList<VariableOrField>> dependencies;
     private final PersistentList<VarFieldDependencies> pendingMerges;
     private final int size;
     private VarFieldDependencies squashed = null;

     private static final Boolean DUMMY_PRESENT = true;
     private static final int MAX_PENDING_MERGES = 100;
     private static final VarFieldDependencies EMPTY_DEPENDENCIES =
         new VarFieldDependencies(PathCopyingPersistentTreeMap.<String, Boolean>of(),
                                  PathCopyingPersistentTreeMap.<ComparableCompositeType, PersistentList<String>>of(),
                                  PathCopyingPersistentTreeMap.<String, Boolean>of(),
                                  PathCopyingPersistentTreeMap.<VariableOrField, PersistentList<VariableOrField>>of(),
                                  PersistentLinkedList.<VarFieldDependencies>of(),
                                  0);
  }

  private static final class CollectingLHSVisitor
    extends DefaultCExpressionVisitor<Pair<VariableOrField, VarFieldDependencies>, RuntimeException> {

    private CollectingLHSVisitor () {

    }

    public static CollectingLHSVisitor instance() {
      return INSTANCE;
    }

    @Override
    public Pair<VariableOrField, VarFieldDependencies> visit(final CArraySubscriptExpression e) {
      final Pair<VariableOrField, VarFieldDependencies> array = e.getArrayExpression().accept(this);
      return Pair.of(array.getFirst(), array.getSecond().withDependencies(
                                                         e.getSubscriptExpression()
                                                          .accept(CollectingRHSVisitor.create(array.getFirst()))));
    }

    @Override
    public Pair<VariableOrField, VarFieldDependencies> visit(final CFieldReference e) {
      final VariableOrField result = VariableOrField.newField(getCanonicalFieldOwnerType(e), e.getFieldName());
      if (e.isPointerDereference()) {
        return Pair.of(result, e.getFieldOwner().accept(CollectingRHSVisitor.create(result)));
      } else {
        return Pair.of(result, e.getFieldOwner().accept(this).getSecond());
      }
    }

    @Override
    public Pair<VariableOrField, VarFieldDependencies> visit(final CPointerExpression e) {
      return Pair.of(VariableOrField.unknown(),
                     e.getOperand().accept(CollectingRHSVisitor.create(VariableOrField.unknown())));
    }

    @Override
    public Pair<VariableOrField, VarFieldDependencies> visit(final CComplexCastExpression e) {
      return e.getOperand().accept(this);
    }

    @Override
    public Pair<VariableOrField, VarFieldDependencies>visit(final CCastExpression e) {
      return e.getOperand().accept(this);
    }

    @Override
    public Pair<VariableOrField, VarFieldDependencies> visit(final CIdExpression e) {
      return Pair.of(VariableOrField.newVariable(e.getDeclaration().getQualifiedName()),
                     VarFieldDependencies.emptyDependencies());
    }

    @Override
    protected Pair<VariableOrField, VarFieldDependencies> visitDefault(final CExpression e)  {
      throw new AssertionError("The expression should not occur in the left hand side");
    }

    private static final CollectingLHSVisitor INSTANCE = new CollectingLHSVisitor();
  }

  private static final class CollectingRHSVisitor
    extends DefaultCExpressionVisitor<VarFieldDependencies, RuntimeException>
    implements CRightHandSideVisitor<VarFieldDependencies, RuntimeException> {

    private CollectingRHSVisitor(final @Nonnull VariableOrField lhs, final boolean addressed) {
      this.lhs = lhs;
      this.addressed = addressed;
    }

    public static CollectingRHSVisitor create(final @Nonnull VariableOrField lhs) {
      return new CollectingRHSVisitor(lhs, false);
    }

    private CollectingRHSVisitor createAddressed() {
      return new CollectingRHSVisitor(lhs, true);
    }

    @Override
    public VarFieldDependencies visit(final CArraySubscriptExpression e) {
      return e.getSubscriptExpression().accept(this).withDependencies(e.getArrayExpression().accept(this));
    }

    @Override
    public VarFieldDependencies visit(final CFieldReference e) {
      return e.getFieldOwner().accept(this).withDependency(lhs,
                                                           VariableOrField.newField(getCanonicalFieldOwnerType(e),
                                                                                    e.getFieldName()));
    }

    @Override
    public VarFieldDependencies visit(final CBinaryExpression e) {
      return e.getOperand1().accept(this).withDependencies(e.getOperand2().accept(this));
    }

    @Override
    public VarFieldDependencies visit(final CUnaryExpression e) {
      if (e.getOperator() != UnaryOperator.AMPER) {
        return e.getOperand().accept(this);
      } else {
        return e.getOperand().accept(createAddressed());
      }
    }

    @Override
    public VarFieldDependencies visit(final CPointerExpression e) {
      return e.getOperand().accept(this);
    }

    @Override
    public VarFieldDependencies visit(final CComplexCastExpression e) {
      return e.getOperand().accept(this);
    }

    @Override
    public VarFieldDependencies visit(final CCastExpression e) {
      return e.getOperand().accept(this);
    }

    @Override
    public VarFieldDependencies visit(final CIdExpression e) {
      final VariableOrField.Variable variable = VariableOrField.newVariable(e.getDeclaration().getQualifiedName());
      final VarFieldDependencies result = VarFieldDependencies.emptyDependencies().withDependency(lhs, variable);
      if (addressed) {
        return result.withAddressedVariable(variable);
      }
      return result;
    }

    @Override
    public VarFieldDependencies visit(CFunctionCallExpression e) {
      VarFieldDependencies result = e.getFunctionNameExpression().accept(this);
      for (CExpression param : e.getParameterExpressions()) {
        result = result.withDependencies(param.accept(this));
      }
      return result;
    }

    @Override
    protected VarFieldDependencies visitDefault(final CExpression e)  {
      return VarFieldDependencies.emptyDependencies();
    }

    private final @Nullable VariableOrField lhs;
    private final boolean addressed;
  }

  private static CCompositeType getCanonicalFieldOwnerType(CFieldReference fieldReference) {
    CType fieldOwnerType = fieldReference.getFieldOwner().getExpressionType().getCanonicalType();

    if (fieldOwnerType instanceof CPointerType) {
      fieldOwnerType = ((CPointerType) fieldOwnerType).getType();
    }
    assert fieldOwnerType instanceof CCompositeType
        : "Field owner should have composite type, but the field-owner type of expression " + fieldReference
          + " in " + fieldReference.getFileLocation()
          + " is " + fieldOwnerType + ", which is a " + fieldOwnerType.getClass().getSimpleName() + ".";
    final CCompositeType compositeType = (CCompositeType) fieldOwnerType;
    // Currently we don't pay attention to possible const and volatile modifiers
    if (compositeType.isConst() || compositeType.isVolatile()) {
      return new CCompositeType(false,
                                false,
                                compositeType.getKind(),
                                compositeType.getMembers(),
                                compositeType.getName(),
                                compositeType.getOrigName());
    } else {
      return compositeType;
    }
  }

  public VarFieldDependencies handleEdge(CFAEdge edge, CFA cfa) throws UnrecognizedCCodeException {
    VarFieldDependencies result = VarFieldDependencies.emptyDependencies();

    switch (edge.getEdgeType()) {

    case AssumeEdge: {
      final CExpression exp = ((CAssumeEdge) edge).getExpression();
      result = result.withDependencies(exp.accept(CollectingRHSVisitor.create(VariableOrField.unknown())));
      break;
    }

    case DeclarationEdge: {
      final CDeclaration decl = ((CDeclarationEdge) edge).getDeclaration();

      if (!(decl instanceof CVariableDeclaration)) {
        break;
      }

      final CVariableDeclaration vdecl = (CVariableDeclaration) decl;
      for (CExpressionAssignmentStatement init : CInitializers.convertToAssignments(vdecl, edge)) {
        Pair<VariableOrField, VarFieldDependencies> r = init.getLeftHandSide()
                                                            .accept(CollectingLHSVisitor.instance());
        result = result.withDependencies(r.getSecond().withDependencies(init.getRightHandSide().accept(
                                                                          CollectingRHSVisitor.create(r.getFirst()))));
      }
      break;
    }

    case StatementEdge: {
      final CStatement statement = ((CStatementEdge) edge).getStatement();

      if (statement instanceof CAssignment) {
        final CAssignment assignment = (CAssignment) statement;
        final CRightHandSide rhs = assignment.getRightHandSide();

        final Pair<VariableOrField, VarFieldDependencies> r = assignment.getLeftHandSide().accept(
                                                                                      CollectingLHSVisitor.instance());

        if (rhs instanceof CExpression || rhs instanceof CFunctionCallExpression) {
          result = result.withDependencies(r.getSecond().withDependencies(rhs.accept(CollectingRHSVisitor
                                                                                              .create(r.getFirst()))));
        } else {
          throw new UnrecognizedCCodeException("Unhandled assignment", edge, assignment);
        }
      } else if (statement instanceof CFunctionCallStatement) {
        ((CFunctionCallStatement) statement).getFunctionCallExpression().accept(CollectingRHSVisitor.create(
                                                                                           VariableOrField.unknown()));
      }

      break;
    }

    case FunctionCallEdge: {
      handleFunctionCallEdge((CFunctionCallEdge) edge);
      break;
    }

    case FunctionReturnEdge: {
      Optional<CVariableDeclaration> returnVar = ((CFunctionReturnEdge)edge).getFunctionEntry().getReturnVariable();
      if (returnVar.isPresent()) {
        String scopedVarName = returnVar.get().getQualifiedName();
        dependencies.addVar(scopedVarName);
        Partition partition = dependencies.getPartitionForVar(scopedVarName);
        partition.addEdge(edge, 0);
      }
      break;
    }

    case ReturnStatementEdge: {
      // this is the 'x' from 'return (x);
      // adding a new temporary FUNCTION_RETURN_VARIABLE, that is not global (-> false)
      CReturnStatementEdge returnStatement = (CReturnStatementEdge) edge;
      if (returnStatement.asAssignment().isPresent()) {
        handleAssignment(edge, returnStatement.asAssignment().get(), cfa);
      }
      break;
    }

    case BlankEdge:
    case CallToReturnEdge:
      // other cases are not interesting
      break;

    default:
      throw new UnrecognizedCCodeException("Unknown edgeType: " + edge.getEdgeType(), edge);
    }

    return result;
  }

  private static final String SCOPE_SEPARATOR = "::";
}
