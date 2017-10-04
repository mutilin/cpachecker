/*
 * CPAchecker is a tool for configurable software verification.
 *  This file is part of CPAchecker.
 *
 *  Copyright (C) 2007-2017  Dirk Beyer
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
package org.sosy_lab.cpachecker.cpa.rcucpa;

import com.google.gson.Gson;
import java.io.IOException;
import java.io.Reader;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.Set;
import java.util.logging.Level;
import org.sosy_lab.common.log.LogManager;
import org.sosy_lab.cpachecker.cfa.CFA;
import org.sosy_lab.cpachecker.cfa.ast.c.CExpression;
import org.sosy_lab.cpachecker.cfa.ast.c.CExpressionAssignmentStatement;
import org.sosy_lab.cpachecker.cfa.ast.c.CFunctionCallAssignmentStatement;
import org.sosy_lab.cpachecker.cfa.ast.c.CFunctionCallExpression;
import org.sosy_lab.cpachecker.cfa.ast.c.CFunctionDeclaration;
import org.sosy_lab.cpachecker.cfa.ast.c.CLeftHandSide;
import org.sosy_lab.cpachecker.cfa.ast.c.CPointerExpression;
import org.sosy_lab.cpachecker.cfa.ast.c.CRightHandSide;
import org.sosy_lab.cpachecker.cfa.ast.c.CStatement;
import org.sosy_lab.cpachecker.cfa.model.CFAEdge;
import org.sosy_lab.cpachecker.cfa.model.CFANode;
import org.sosy_lab.cpachecker.cfa.model.c.CFunctionCallEdge;
import org.sosy_lab.cpachecker.cfa.model.c.CStatementEdge;
import org.sosy_lab.cpachecker.cfa.types.c.CPointerType;
import org.sosy_lab.cpachecker.cpa.pointer2.PointerStatistics;
import org.sosy_lab.cpachecker.cpa.pointer2.util.LocationSet;
import org.sosy_lab.cpachecker.util.states.MemoryLocation;

class PreRCUAnalysis {

  static Set<MemoryLocation> getRCUAndAliases(CFA cfa, Path input, LogManager logger) {
    Map<MemoryLocation, Set<MemoryLocation>> pointsTo = parseFile(input, logger);
    Map<MemoryLocation, Set<MemoryLocation>> aliases = getAliases(pointsTo);
    Set<MemoryLocation> rcuPointers = runPreAnalysis(cfa, logger);

    for (MemoryLocation pointer : rcuPointers) {
      if (!aliases.containsKey(pointer)) {
        logger.log(Level.WARNING, "No RCU pointer <" + pointer.toString() + "> in aliases");
      } else {
        rcuPointers.addAll(aliases.get(pointer));
      }
    }

    logger.log(Level.ALL, "RCU with aliases: " + rcuPointers);

    return rcuPointers;
  }

  private static Map<MemoryLocation, Set<MemoryLocation>> getAliases(Map<MemoryLocation,
                                                                     Set<MemoryLocation>> pointsTo) {
    Map<MemoryLocation, Set<MemoryLocation>> aliases = new HashMap<>();
    for (MemoryLocation pointer : pointsTo.keySet()) {
      Set<MemoryLocation> pointerPointTo = pointsTo.get(pointer);
      if (pointerPointTo.contains(PointerStatistics.getReplLocSetTop())) {
        // pointer can point anywhere
        aliases.put(pointer, new HashSet<>(pointsTo.keySet()));
        for (MemoryLocation other : pointsTo.keySet()) {
          pointsTo.putIfAbsent(other, new HashSet<>());
          pointsTo.get(other).add(pointer);
        }
      } else if (!pointerPointTo.contains(PointerStatistics.getReplLocSetBot())) {
        Set<MemoryLocation> commonElems;
        for (MemoryLocation other : pointsTo.keySet()) {
          if (!other.equals(pointer)) {
            commonElems = new HashSet<>(pointsTo.get(other));
            commonElems.retainAll(pointerPointTo);
            if (!commonElems.isEmpty()) {
              addAlias(aliases, pointer, other);
              addAlias(aliases, other, pointer);
            }
          }
        }
      }
    }
    return aliases;
  }

  private static void addAlias(Map<MemoryLocation, Set<MemoryLocation>> aliases,
                               MemoryLocation one,
                               MemoryLocation other) {
    if (!aliases.containsKey(one)) {
      aliases.put(one, new HashSet<>());
    }
    aliases.get(one).add(other);
  }

  private static Map<MemoryLocation, Set<MemoryLocation>> parseFile(Path input, LogManager logger) {
    Map<MemoryLocation, Set<MemoryLocation>> result = new HashMap<>();
    try (Reader reader = Files.newBufferedReader(input, Charset.defaultCharset())) {
      Gson builder = new Gson();
      Map<String, Map<String, List<Map<String, String>>>> map = builder.fromJson(reader, Map.class);
      for (String key : map.keySet()) {
        Map<String, List<Map<String, String>>> newMap = map.get(key);
        Set<MemoryLocation> set = new HashSet<>();
        for (String key2 : newMap.keySet()) {
          for (Map<String, String> elem :  newMap.get(key2)) {
            String fname = null;
            String id = null;
            Long offset = null;
            MemoryLocation loc;
            if (elem.containsKey("functionName")) {
              fname = elem.get("functionName");
            }
            if (elem.containsKey("identifier")) {
              id = elem.get("identifier");
            }
            if (elem.containsKey("offset")) {
              offset = new Long(elem.get("offset"));
            }

            if (fname != null && offset != null) {
              loc = MemoryLocation.valueOf(fname, id, offset);
            } else if (offset != null) {
              loc = MemoryLocation.valueOf(id, offset);
            } else if (fname != null){
              loc = MemoryLocation.valueOf(fname, id);
            } else {
              loc = MemoryLocation.valueOf(id);
            }
            set.add(loc);
          }
        }
        MemoryLocation locKey = MemoryLocation.valueOf(key);
        result.put(locKey, set);
      }
      logger.log(Level.ALL, "GSON read: " + map);
      logger.log(Level.ALL, "Parsed: " + result);
    } catch (IOException pE) {
      pE.printStackTrace();
    }
    return result;
  }

  private static Set<MemoryLocation> runPreAnalysis(CFA pCfa, LogManager pLogger) {
    Set<MemoryLocation> rcuPointers = new HashSet<>();
    for (CFANode node : pCfa.getAllNodes()) {
      for (int i = 0; i < node.getNumEnteringEdges(); ++i) {
        CFAEdge edge = node.getEnteringEdge(i);
        pLogger.log(Level.ALL, "EDGE TYPE: " + edge.getEdgeType());
        pLogger.log(Level.ALL, "EDGE CONT: " + edge.getRawStatement());
        switch(edge.getEdgeType()) {
          case StatementEdge:
            handleStatementEdge((CStatementEdge) edge,
                                edge.getPredecessor().getFunctionName(),
                                rcuPointers, pLogger);
            break;
          case FunctionCallEdge:
            handleFunctionCallEdge((CFunctionCallEdge) edge,
                                    edge.getPredecessor().getFunctionName(),
                                    rcuPointers, pLogger);
          default:
            break;
        }
      }
    }
    return rcuPointers;
  }

  private static void handleFunctionCallEdge(CFunctionCallEdge pEdge,
                                             String pFunctionName,
                                             Set<MemoryLocation> pRcuPointers,
                                             LogManager pLogger) {
    // rcu_assign_pointer(gp, p)
    CFunctionCallExpression fc = pEdge.getSummaryEdge().getExpression().getFunctionCallExpression();
    CFunctionDeclaration fd = fc.getDeclaration();
    if (fd.getName().contains("ldv_rcu_assign_pointer")) {
      List<CExpression> params = fc.getParameterExpressions();

      //WARNING: not-really-a-clever-hack detected

      MemoryLocation loc = MemoryLocation.valueOf(pFunctionName, params.get(0).toString());
      pRcuPointers.add(loc);

      loc = MemoryLocation.valueOf(pFunctionName, params.get(1).toString());
      pRcuPointers.add(loc);
    }
  }

  private static void handleStatementEdge(CStatementEdge pEdge,
                                          String pFunctionName,
                                          Set<MemoryLocation> pRcuPointers,
                                          LogManager pLogger) {
    // p = rcu_dereference(gp)
    CStatement statement = pEdge.getStatement();
    pLogger.log(Level.ALL, "HANDLE_STATEMENT: " + statement.getClass()
                            + ' ' + statement.toString());
    if (statement instanceof CFunctionCallAssignmentStatement) {
      CFunctionCallAssignmentStatement assignment = (CFunctionCallAssignmentStatement) statement;
      CLeftHandSide leftHandSide = assignment.getLeftHandSide();
      if (leftHandSide.getExpressionType() instanceof CPointerType) {
        CFunctionCallExpression funcExpr = assignment.getFunctionCallExpression();

        pLogger.log(Level.ALL,"FUNC NAME EXPR: " + funcExpr.getFunctionNameExpression());

        if (funcExpr.getFunctionNameExpression().toString().contains("ldv_rcu_dereference")) {
          CExpression rcuPtr = funcExpr.getParameterExpressions().get(0);

          //WARNING: not-really-a-clever-hack detected

          MemoryLocation loc = MemoryLocation.valueOf(pFunctionName, rcuPtr.toString());

          pLogger.log(Level.ALL, "RCU PTR: " + rcuPtr);
          pLogger.log(Level.ALL, "MEM LOC: " + loc);

          pRcuPointers.add(loc);

          loc = MemoryLocation.valueOf(pFunctionName, leftHandSide.toString());

          pLogger.log(Level.ALL, "LHS PTR: " + leftHandSide.toString());
          pLogger.log(Level.ALL, "LHS MEM LOC: " + loc);

          pRcuPointers.add(loc);
        }
      }
    }
  }


}
