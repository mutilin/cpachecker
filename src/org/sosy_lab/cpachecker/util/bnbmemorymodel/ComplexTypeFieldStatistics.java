/*
 *  CPAchecker is a tool for configurable software verification.
 *  This file is part of CPAchecker.
 *
 *  Copyright (C) 2007-2015  Dirk Beyer
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
package org.sosy_lab.cpachecker.util.bnbmemorymodel;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;

import org.sosy_lab.cpachecker.cfa.CFA;
import org.sosy_lab.cpachecker.cfa.ast.c.CBinaryExpression;
import org.sosy_lab.cpachecker.cfa.ast.c.CCastExpression;
import org.sosy_lab.cpachecker.cfa.ast.c.CComplexCastExpression;
import org.sosy_lab.cpachecker.cfa.ast.c.CDeclaration;
import org.sosy_lab.cpachecker.cfa.ast.c.CExpression;
import org.sosy_lab.cpachecker.cfa.ast.c.CExpressionAssignmentStatement;
import org.sosy_lab.cpachecker.cfa.ast.c.CFieldReference;
import org.sosy_lab.cpachecker.cfa.ast.c.CFunctionCallAssignmentStatement;
import org.sosy_lab.cpachecker.cfa.ast.c.CFunctionCallStatement;
import org.sosy_lab.cpachecker.cfa.ast.c.CInitializer;
import org.sosy_lab.cpachecker.cfa.ast.c.CInitializerExpression;
import org.sosy_lab.cpachecker.cfa.ast.c.CPointerExpression;
import org.sosy_lab.cpachecker.cfa.ast.c.CStatement;
import org.sosy_lab.cpachecker.cfa.ast.c.CUnaryExpression;
import org.sosy_lab.cpachecker.cfa.ast.c.CUnaryExpression.UnaryOperator;
import org.sosy_lab.cpachecker.cfa.ast.c.CVariableDeclaration;
import org.sosy_lab.cpachecker.cfa.model.CFAEdge;
import org.sosy_lab.cpachecker.cfa.model.CFAEdgeType;
import org.sosy_lab.cpachecker.cfa.model.CFANode;
import org.sosy_lab.cpachecker.cfa.model.MultiEdge;
import org.sosy_lab.cpachecker.cfa.model.c.CDeclarationEdge;
import org.sosy_lab.cpachecker.cfa.model.c.CFunctionCallEdge;
import org.sosy_lab.cpachecker.cfa.model.c.CStatementEdge;
import org.sosy_lab.cpachecker.cfa.types.c.CElaboratedType;
import org.sosy_lab.cpachecker.cfa.types.c.CPointerType;
import org.sosy_lab.cpachecker.cfa.types.c.CType;
import org.sosy_lab.cpachecker.cfa.types.c.CTypedefType;

public class ComplexTypeFieldStatistics {

  private Map<CType, HashMap<CType, HashSet<String>>> usedFields = new HashMap<>();
  private Map<CType, HashMap<CType, HashSet<String>>> refdFields = new HashMap<>();

  public void findFieldsInCFA(CFA cfa){
    CFAEdge edge;

    for (CFANode node : cfa.getAllNodes()){
      for (int i = 0; i < node.getNumEnteringEdges(); ++i){
        edge = node.getEnteringEdge(i);
        if (edge.getEdgeType() == CFAEdgeType.MultiEdge){
          for (CFAEdge insideEdge : ((MultiEdge)edge).getEdges()){
            visitEdge(insideEdge);
          }
        } else {
          visitEdge(edge);
        }
      }
    }
  }

  private void visitEdge(CFAEdge edge) {
    CFAEdgeType edgeType;
    CStatement statement;
    edgeType = edge.getEdgeType();

    if (edgeType == CFAEdgeType.StatementEdge){
       //Searching for address-taking and calling of the structure field
      statement = ((CStatementEdge)edge).getStatement();
      if (statement instanceof CExpressionAssignmentStatement){
        chooser(((CExpressionAssignmentStatement)statement).getRightHandSide());
        chooser(((CExpressionAssignmentStatement)statement).getLeftHandSide());
      } else if (statement instanceof CFunctionCallStatement){
        for (CExpression param : ((CFunctionCallStatement)statement)
                                    .getFunctionCallExpression().getParameterExpressions()){
          chooser(param);
        }
      } else if (statement instanceof CFunctionCallAssignmentStatement){
        chooser(((CFunctionCallAssignmentStatement)statement).getLeftHandSide());
        for (CExpression param : ((CFunctionCallAssignmentStatement)statement)
                                    .getFunctionCallExpression().getParameterExpressions()){
          chooser(param);
        }
      }
    } else if (edgeType == CFAEdgeType.DeclarationEdge){
      visit(((CDeclarationEdge)edge).getDeclaration());
    } else if (edgeType == CFAEdgeType.FunctionCallEdge){
      for (CExpression param : ((CFunctionCallEdge)edge).getArguments()){
        chooser(param);
      }
    }
  }

  private void chooser(CExpression param) {
    //TODO: make chooser a class that implements Visitor-type interfaces
    if (param instanceof CUnaryExpression){
      visit((CUnaryExpression) param);
    } else if (param instanceof CFieldReference){
      visit((CFieldReference) param, false);
    } else if (param instanceof CBinaryExpression){
      visit((CBinaryExpression) param);
    } else if (param instanceof CCastExpression){
      chooser(((CCastExpression)param).getOperand());
    } else if (param instanceof CComplexCastExpression){
      chooser(((CComplexCastExpression) param).getOperand());
    } else if (param instanceof CPointerExpression){
      visit((CPointerExpression) param);
    }
  }

  private void visit(CPointerExpression expr) {
    //System.out.println("VVV: " + expr.getOperand());
    chooser(expr.getOperand());
  }

  private void visit(CBinaryExpression bin){
    chooser(bin.getOperand1());
    chooser(bin.getOperand2());
  }

  private void visit(CUnaryExpression expr){
    if (expr.getOperator() == UnaryOperator.AMPER){
      if (expr.getOperand() instanceof CFieldReference){
        visit((CFieldReference)expr.getOperand(), true);
      }
    }
  }

  private void visit(CFieldReference ref, boolean referenced){
    CExpression parent;
    CType parentType;

    //System.out.println("LLL:" + ref.toString() + ' ' + referenced);
    parent = ref.getFieldOwner();

    if (parent == null){
      return;
    }

    parentType = parent.getExpressionType();

    while (parentType instanceof CPointerType){
      parentType = ((CPointerType) parentType).getType();
    }
    while (parentType instanceof CTypedefType){
      parentType = ((CTypedefType) parentType).getRealType();
    }
    while (parentType instanceof CElaboratedType){
      parentType = ((CElaboratedType) parentType).getRealType();
    }

    CType fieldType = ref.getExpressionType();
    if (!referenced) {

      if (! usedFields.containsKey(fieldType)){
        usedFields.put(fieldType, new HashMap<CType, HashSet<String>>());
      }

      if (! usedFields.get(fieldType).containsKey(parentType)){
        usedFields.get(fieldType).put(parentType, new HashSet<String>());
      }

      usedFields.get(fieldType).get(parentType).add(ref.getFieldName());

    } else {

      if (! refdFields.containsKey(fieldType)){
        refdFields.put(fieldType, new HashMap<CType, HashSet<String>>());
      }

      if (! refdFields.get(fieldType).containsKey(parentType)){
        refdFields.get(fieldType).put(parentType, new HashSet<String>());
      }

      refdFields.get(fieldType).get(parentType).add(ref.getFieldName());

    }

    chooser(parent);
  }

  private void visit(CDeclaration decl){
    if (decl instanceof CVariableDeclaration){
      CInitializer init = ((CVariableDeclaration) decl).getInitializer();
      if (init != null && init instanceof CInitializerExpression){
        chooser(((CInitializerExpression) init).getExpression());
      }
    }
  }

  public void dumpStat(String filename){
    File dump = new File(filename);

    try {
      FileWriter writer = new FileWriter(dump);

      Map<CType, HashSet<String>> sub;
      String output = "";
      String sub_output;
      int used;

      output += "USED_FIELDS:\n";
      for (CType type : usedFields.keySet()){
        sub = usedFields.get(type);
        used = 0;
        sub_output = "";
        for (CType struct_name : sub.keySet()){
          sub_output += "\t\tSTRUCT: " + struct_name + '\n';
          used += sub.get(struct_name).size();
          for (String fieldName : sub.get(struct_name)){
            sub_output += "\t\t\tFIELD: " + fieldName + '\n';
          }
        }
        output += "\tFIELD_TYPE: " + type + "\n\tTIMES USED: " + used + '\n' + sub_output;
      }

      output += "\nREFERENCED_FIELDS:\n";
      for (CType type : refdFields.keySet()){
        sub = refdFields.get(type);
        used = 0;
        sub_output = "";
        for (CType struct_name : sub.keySet()){
          sub_output += "\t\tSTRUCT: " + struct_name + '\n';
          used += sub.get(struct_name).size();
          for (String fieldName : sub.get(struct_name)){
            sub_output += "\t\t\tFIELD: " + fieldName + '\n';
          }
        }
        output += "\tFIELD_TYPE: " + type + "\n\tTIMES USED: " + used + '\n' + sub_output;
      }

      writer.write(output);
      writer.close();

    } catch (IOException e) {
      System.out.println(e.getMessage());
      e.printStackTrace();
    }

  }


  public Map<CType, HashMap<CType, HashSet<String>>> getUsedFields() {
    return usedFields;
  }


  public Map<CType, HashMap<CType, HashSet<String>>> getRefdFields() {
    return refdFields;
  }
}