/*
 *  CPAchecker is a tool for configurable software verification.
 *  This file is part of CPAchecker.
 *
 *  Copyright (C) 2007-2020  Dirk Beyer
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
 */
package org.sosy_lab.cpachecker.cfa;

import com.google.common.collect.Sets;
import com.google.common.collect.SortedSetMultimap;
import com.google.common.collect.TreeMultimap;
import de.uni_freiburg.informatik.ultimate.smtinterpol.util.IdentityHashSet;
import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.logging.Level;
import org.sosy_lab.common.ShutdownNotifier;
import org.sosy_lab.common.configuration.Configuration;
import org.sosy_lab.common.configuration.InvalidConfigurationException;
import org.sosy_lab.common.configuration.Options;
import org.sosy_lab.common.log.LogManager;
import org.sosy_lab.cpachecker.cfa.ast.FileLocation;
import org.sosy_lab.cpachecker.cfa.model.BlankEdge;
import org.sosy_lab.cpachecker.cfa.model.CFAEdge;
import org.sosy_lab.cpachecker.cfa.model.CFAEdgeType;
import org.sosy_lab.cpachecker.cfa.model.CFANode;
import org.sosy_lab.cpachecker.cfa.model.FunctionEntryNode;
import org.sosy_lab.cpachecker.cfa.model.FunctionExitNode;
import org.sosy_lab.cpachecker.cfa.model.c.CAssumeEdge;
import org.sosy_lab.cpachecker.cfa.model.c.CDeclarationEdge;
import org.sosy_lab.cpachecker.cfa.model.c.CFunctionCallEdge;
import org.sosy_lab.cpachecker.cfa.model.c.CFunctionEntryNode;
import org.sosy_lab.cpachecker.cfa.model.c.CFunctionReturnEdge;
import org.sosy_lab.cpachecker.cfa.model.c.CReturnStatementEdge;
import org.sosy_lab.cpachecker.cfa.model.c.CStatementEdge;
import org.sosy_lab.cpachecker.exceptions.ParserException;
import org.sosy_lab.cpachecker.util.CFATraversal;
import org.sosy_lab.cpachecker.util.CFATraversal.CompositeCFAVisitor;
import org.sosy_lab.cpachecker.util.CFATraversal.EdgeCollectingCFAVisitor;
import org.sosy_lab.cpachecker.util.CFATraversal.NodeCollectingCFAVisitor;

@Options
public class CFAMutator extends CFACreator {
  private enum MutationType {
    NodeAndEdgeRemoval,
    EdgeBlanking,
    EdgeSticking
  }

  private ParseResult parseResult = null;
  private Set<CFANode> originalNodes = null;
  private Set<CFAEdge> originalEdges = null;

  private MutationType lastMutation = null;
  private CFAEdge lastRemovedEdge = null;
  private Set<CFAEdge> restoredEdges = new HashSet<>();
  private CFA lastCFA = null;
  private boolean doLastRun = false;

  public CFAMutator(Configuration pConfig, LogManager pLogger, ShutdownNotifier pShutdownNotifier)
      throws InvalidConfigurationException {
    super(pConfig, pLogger, pShutdownNotifier);
    // TODO Auto-generated constructor stub
  }

  private void removeEdge(CFAEdge pEdge) {
    logger.logf(Level.FINER, "removing edge %s", pEdge);
    CFACreationUtils.removeEdgeFromNodes(pEdge);
    originalEdges.remove(pEdge);
  }

  private void addEdge(CFAEdge pEdge) {
    logger.logf(Level.FINER, "adding edge %s", pEdge);
    CFACreationUtils.addEdgeUnconditionallyToCFA(pEdge);
    originalEdges.add(pEdge);
  }

  private void removeNode(SortedSetMultimap<String, CFANode> nodes, CFANode n) {
    logger.logf(Level.FINER, "removing node %s", n);
    nodes.remove(n.getFunctionName(), n);
    originalNodes.remove(n);
  }

  private void addNode(SortedSetMultimap<String, CFANode> nodes, CFANode removedNode) {
    logger.logf(Level.FINER, "adding node %s", removedNode);
    nodes.put(removedNode.getFunctionName(), removedNode);
    originalNodes.add(removedNode);
  }

  // kind of main function

  @Override
  protected ParseResult parseToCFAs(final List<String> sourceFiles)
      throws InvalidConfigurationException, IOException, ParserException, InterruptedException {

    if (parseResult == null) { // do zero-th run, init

      parseResult = super.parseToCFAs(sourceFiles);
      originalNodes = new HashSet<>(parseResult.getCFANodes().values());

      final EdgeCollectingCFAVisitor visitor = new EdgeCollectingCFAVisitor();
      for (final FunctionEntryNode entryNode : parseResult.getFunctions().values()) {
        CFATraversal.dfs().traverseOnce(entryNode, visitor);
      }

      originalEdges = Sets.newIdentityHashSet();
      originalEdges.addAll(visitor.getVisitedEdges());
      return parseResult;

    } else if (!doLastRun) { // do non-last run
      clearParseResultAfterPostprocessings();

      if (!mutate()) {
        doLastRun = true;
      }
      // need to return after possible rollback
      return parseResult;

    } else { // do last run
      exportCFAAsync(lastCFA);
      return null;
    }
  }


  private void clearParseResultAfterPostprocessings() {
    final EdgeCollectingCFAVisitor edgeCollector = new EdgeCollectingCFAVisitor();
    final NodeCollectingCFAVisitor nodeCollector = new NodeCollectingCFAVisitor();
    final CFATraversal.CompositeCFAVisitor visitor =
        new CompositeCFAVisitor(edgeCollector, nodeCollector);

    for (final FunctionEntryNode entryNode : parseResult.getFunctions().values()) {
      CFATraversal.dfs().traverse(entryNode, visitor);
    }

    Set<CFAEdge> tmpSet = new IdentityHashSet<>();
    tmpSet.addAll(edgeCollector.getVisitedEdges());
    final Set<CFAEdge> edgesToRemove =
        Sets.difference(tmpSet, originalEdges);
    final Set<CFAEdge> edgesToAdd = Sets.difference(originalEdges, tmpSet);
    final Set<CFANode> nodesToRemove =
        Sets.difference(new HashSet<>(nodeCollector.getVisitedNodes()), originalNodes);

    // finally remove nodes and edges added as global decl. and interprocedural
    SortedSetMultimap<String, CFANode> nodes = parseResult.getCFANodes();
    for (CFANode n : nodesToRemove) {
      logger.logf(
          Level.FINEST,
          "clearing: removing node %s (was present: %s)",
          n,
          nodes.remove(n.getFunctionName(), n));
    }
    for (CFAEdge e : edgesToRemove) {
      logger.logf(Level.FINEST, "clearing: removing edge %s", e);
      CFACreationUtils.removeEdgeFromNodes(e);
    }
    for (CFAEdge e : edgesToAdd) {
      logger.logf(Level.FINEST, "clearing: returning edge %s", e);
      CFACreationUtils.addEdgeUnconditionallyToCFA(e);
    }
  }

  // try to do simple mutation:
  // remove an edge, or blank one if it's between two assume edges' successors
  private ParseResult removeAnEdge(ParseResult pParseResult1) {
    SortedSetMultimap<String, CFANode> nodes = TreeMultimap.create(pParseResult1.getCFANodes());

    boolean canMutate = false;

    for (CFANode node : pParseResult1.getCFANodes().values()) {
      if (node.getNumLeavingEdges() != 1 || node.getNumEnteringEdges() != 1) {
        continue;
      }

      CFAEdge leavingEdge = node.getLeavingEdge(0);
      if (restoredEdges.contains(leavingEdge)) {
        logger.logf(Level.FINEST, "skipping restored edge %s after node %s", leavingEdge, node);
        continue;
      }

      CFANode successor = leavingEdge.getSuccessor();
      CFAEdge enteringEdge = node.getEnteringEdge(0);
      CFANode predecessor = enteringEdge.getPredecessor();

      // TODO can't duplicate or remove such edges
      if (enteringEdge.getEdgeType() == CFAEdgeType.ReturnStatementEdge
          || enteringEdge.getEdgeType() == CFAEdgeType.FunctionCallEdge
          || enteringEdge.getEdgeType() == CFAEdgeType.FunctionReturnEdge
          || enteringEdge.getEdgeType() == CFAEdgeType.CallToReturnEdge
          || leavingEdge.getEdgeType() == CFAEdgeType.ReturnStatementEdge
          || leavingEdge.getEdgeType() == CFAEdgeType.FunctionCallEdge
          || leavingEdge.getEdgeType() == CFAEdgeType.FunctionReturnEdge
          || leavingEdge.getEdgeType() == CFAEdgeType.CallToReturnEdge) {
        continue;
      }

      if (!predecessor.getFunctionName().equals(node.getFunctionName())
          || !successor.getFunctionName().equals(node.getFunctionName())) {
        System.out.println(
            enteringEdge + ", type " + enteringEdge.getEdgeType() + " and " + leavingEdge);
        continue;
      }

      if (predecessor.hasEdgeTo(successor)) {
        if (leavingEdge.getEdgeType() == CFAEdgeType.BlankEdge) {
          // stickAssumeEdgesIntoOne(predecessor, successor); TODO
          continue;
        } else {
          blankifyEdge(leavingEdge);
        }
      } else {
        removeLeavingEdgeAndConnectEnteringEdgeAround(node);
        removeNode(nodes, node);
      }
      canMutate = true;
      break;
    }

    if (!canMutate) {
      logger.log(Level.INFO, "no edge was removed or blanked");
      return null;
    }
    return new ParseResult(
        pParseResult1.getFunctions(),
        nodes,
        pParseResult1.getGlobalDeclarations(),
        pParseResult1.getFileNames());
  }

  private boolean mutate() {
    ParseResult r = removeAnEdge(parseResult);
    if (r == null) {
      return false;
    } else {
      parseResult = r;
      return true;
    }
  }

  // undo last mutation
  public void rollback() {
    clearParseResultAfterPostprocessings();
    if (lastMutation == MutationType.NodeAndEdgeRemoval) {
      parseResult = returnEdge(parseResult);
    } else {
      parseResult = refillEdge(parseResult);
    }
  }

  // remove the node with its only leaving and entering edges
  // and insert new edge similar to entering edge.
  private void removeLeavingEdgeAndConnectEnteringEdgeAround(CFANode pNode) {
    lastMutation = MutationType.NodeAndEdgeRemoval;
    lastRemovedEdge = pNode.getLeavingEdge(0);
    logger.logf(Level.INFO, "removing %s with edge %s", pNode, lastRemovedEdge);

    CFANode successor = lastRemovedEdge.getSuccessor();
    CFAEdge enteringEdge = pNode.getEnteringEdge(0);

    CFAEdge newEdge = dupEdge(enteringEdge, null, successor);
    removeEdge(enteringEdge);
    addEdge(newEdge);
    removeEdge(lastRemovedEdge);
  }

  // undo removing a node with leaving edge
  private ParseResult returnEdge(ParseResult pParseResult) {
    logger.logf(Level.FINE, "returning edge %s", lastRemovedEdge);
    // undo mutation: insert node, reconnect edges, insert lastRemovedEdge
    SortedSetMultimap<String, CFANode> nodes = TreeMultimap.create(pParseResult.getCFANodes());

    CFANode removedNode = lastRemovedEdge.getPredecessor();
    addNode(nodes, removedNode);

    CFANode successor = lastRemovedEdge.getSuccessor();
    assert successor.getNumEnteringEdges() > 0;
    CFAEdge insertedEdge = successor.getEnteringEdge(0);

    removeEdge(insertedEdge);
    CFAEdge firstEdge = dupEdge(insertedEdge, null, removedNode);

    addEdge(firstEdge);
    addEdge(lastRemovedEdge);
    restoredEdges.add(lastRemovedEdge);

    return new ParseResult(
        pParseResult.getFunctions(),
        nodes,
        pParseResult.getGlobalDeclarations(),
        pParseResult.getFileNames());
  }

  // replace the edge with blank edge
  private void blankifyEdge(CFAEdge pEdge) {
    lastMutation = MutationType.EdgeBlanking;
    lastRemovedEdge = pEdge;
    logger.logf(Level.INFO, "blanking edge %s", pEdge);

    CFAEdge newEdge =
        new BlankEdge(
            "",
            FileLocation.DUMMY,
            pEdge.getPredecessor(),
            pEdge.getSuccessor(),
            "CFAMutator blanked this edge");

    removeEdge(pEdge);
    addEdge(newEdge);
  }

  // undo blanking an edge
  private ParseResult refillEdge(ParseResult pParseResult) {
    logger.logf(Level.FINE, "refilling edge %s", lastRemovedEdge);
    CFAEdge blank = lastRemovedEdge.getPredecessor().getLeavingEdge(0);
    restoredEdges.add(lastRemovedEdge);

    removeEdge(blank);
    addEdge(lastRemovedEdge);

    return pParseResult;
  }

  // return an edge with same "contents" but from pPredNode to pSuccNode
  private CFAEdge dupEdge(CFAEdge pEdge, CFANode pPredNode, CFANode pSuccNode) {
    if (pPredNode == null) {
      pPredNode = pEdge.getPredecessor();
    }
    if (pSuccNode == null) {
      pSuccNode = pEdge.getSuccessor();
    }

    assert pPredNode.getFunctionName().equals(pSuccNode.getFunctionName());

    CFAEdge newEdge = new DummyCFAEdge(pPredNode, pSuccNode);

    switch (pEdge.getEdgeType()) {
      case AssumeEdge:
        if (pEdge instanceof CAssumeEdge) {
          CAssumeEdge cAssumeEdge = (CAssumeEdge) pEdge;
          newEdge =
              new CAssumeEdge(
                  cAssumeEdge.getRawStatement(),
                  cAssumeEdge.getFileLocation(),
                  cAssumeEdge.getPredecessor(),
                  pSuccNode,
                  cAssumeEdge.getExpression(),
                  cAssumeEdge.getTruthAssumption(),
                  cAssumeEdge.isSwapped(),
                  cAssumeEdge.isArtificialIntermediate());
        } else {
          // TODO JAssumeEdge
          throw new UnsupportedOperationException("JAssumeEdge");
        }
        break;
      case BlankEdge:
        BlankEdge blankEdge = (BlankEdge) pEdge;
        newEdge =
            new BlankEdge(
                blankEdge.getRawStatement(),
                blankEdge.getFileLocation(),
                blankEdge.getPredecessor(),
                pSuccNode,
                blankEdge.getDescription());
        break;
      case DeclarationEdge:
        if (pEdge instanceof CDeclarationEdge) {
          CDeclarationEdge cDeclarationEdge = (CDeclarationEdge) pEdge;
          newEdge =
              new CDeclarationEdge(
                  cDeclarationEdge.getRawStatement(),
                  cDeclarationEdge.getFileLocation(),
                  cDeclarationEdge.getPredecessor(),
                  pSuccNode,
                  cDeclarationEdge.getDeclaration());
        } else {
          // TODO JDeclarationEdge
          throw new UnsupportedOperationException("JDeclarationEdge");
        }
        break;
      case FunctionCallEdge:
        if (pEdge instanceof CFunctionCallEdge) {
          CFunctionCallEdge cFunctionCallEdge = (CFunctionCallEdge) pEdge;
          newEdge =
              new CFunctionCallEdge(
                  cFunctionCallEdge.getRawStatement(),
                  cFunctionCallEdge.getFileLocation(),
                  cFunctionCallEdge.getPredecessor(),
                  (CFunctionEntryNode) pSuccNode, // TODO?
                  cFunctionCallEdge.getRawAST().get(),
                  cFunctionCallEdge.getSummaryEdge());
        } else {
          // TODO JMethodCallEdge
          throw new UnsupportedOperationException("JMethodCallEdge");
        }
        break;
      case FunctionReturnEdge:
        if (pEdge instanceof CFunctionReturnEdge) {
          CFunctionReturnEdge cFunctionReturnEdge = (CFunctionReturnEdge) pEdge;
          newEdge =
              new CFunctionReturnEdge(
                  cFunctionReturnEdge.getFileLocation(),
                  cFunctionReturnEdge.getPredecessor(),
                  pSuccNode,
                  cFunctionReturnEdge.getSummaryEdge());
        } else {
          // TODO JMethodReturnEdge
          throw new UnsupportedOperationException("JMethodReturnEdge");
        }
        break;
      case ReturnStatementEdge:
        if (pEdge instanceof CReturnStatementEdge) {
          CReturnStatementEdge cRerurnStatementEdge = (CReturnStatementEdge) pEdge;
          System.out.println("reconnecting edge " + pEdge + " to " + pSuccNode);
          System.out.flush();

          newEdge =
              new CReturnStatementEdge(
                  cRerurnStatementEdge.getRawStatement(),
                  cRerurnStatementEdge.getRawAST().get(),
                  cRerurnStatementEdge.getFileLocation(),
                  cRerurnStatementEdge.getPredecessor(),
                  (FunctionExitNode) pSuccNode); // TODO?
        } else {
          // TODO JReturnStatementEdge
          throw new UnsupportedOperationException("JReturnStatementEdge");
        }
        break;
      case StatementEdge:
        if (pEdge instanceof CStatementEdge) {
          CStatementEdge cStatementEdge = (CStatementEdge) pEdge;
          newEdge =
              new CStatementEdge(
                  cStatementEdge.getRawStatement(),
                  cStatementEdge.getStatement(),
                  cStatementEdge.getFileLocation(),
                  cStatementEdge.getPredecessor(),
                  pSuccNode);
        } else {
          // TODO JStatementEdge
          throw new UnsupportedOperationException("JStatementEdge");
        }
        break;
      case CallToReturnEdge:
        throw new UnsupportedOperationException("SummaryEdge");
      default:
        throw new UnsupportedOperationException("Unknown Type of edge: " + pEdge.getEdgeType());
    }

    logger.logf(Level.FINER, "duplicated edge " + pEdge + " as " + newEdge);

    return newEdge;
  }

  @Override
  protected void exportCFAAsync(final CFA cfa) {
    logger.logf(Level.FINE, "Count of CFA nodes: %d", cfa.getAllNodes().size());

    if (doLastRun) {
      super.exportCFAAsync(cfa);
    } else {
      lastCFA = cfa;
    }
  }

  //  private void stickAssumeEdgesIntoOne(CFANode pPredecessor, CFANode pSuccessor) {
  //    lastMutation = MutationType.EdgeSticking;
  //    CFAEdge left = pPredecessor.getLeavingEdge(0);
  //    CFAEdge right = pPredecessor.getLeavingEdge(1);
  //
  //    if (left.getSuccessor() == pSuccessor) {
  //      lastRemovedEdge = right;
  //    } else {
  //      lastRemovedEdge = left;
  //    }
  //    // TODO
  //  }

  // private ParseResult removeFunction(ParseResult pParseResult) {
  // NavigableMap<String, FunctionEntryNode> func = new TreeMap<>(pParseResult.getFunctions());
  // logger.logf(Level.FINE, "removing " + func.firstEntry().getKey());
  // func.remove(func.firstEntry().getKey());
  // ParseResult ans =        new ParseResult(
  //
  // func,pParseResult.getCFANodes(),pParseResult.getGlobalDeclarations(),pParseResult.getFileNames());
  // return ans;
  // }
}
