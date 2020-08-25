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
package org.sosy_lab.cpachecker.cpa.notbam;

import com.google.common.base.Predicate;
import com.google.common.base.Predicates;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.NavigableSet;
import java.util.Set;
import java.util.TreeSet;
import org.sosy_lab.cpachecker.cpa.arg.ARGReachedSet;
import org.sosy_lab.cpachecker.cpa.arg.ARGState;
import org.sosy_lab.cpachecker.cpa.bam.AbstractBAMCPA;
import org.sosy_lab.cpachecker.cpa.bam.BAMSubgraphComputer;
import org.sosy_lab.cpachecker.cpa.notbam.NBAMCacheManager.Entry;
import org.sosy_lab.cpachecker.cpa.notbam.NBAMExtendedState.BlockEntry;

public class NBAMSubgraphComputer extends BAMSubgraphComputer {
  private final NBAMCacheManager cache;

  public NBAMSubgraphComputer(AbstractBAMCPA pBamCpa) {
    super(pBamCpa, false);
    cache = ((NotBAMCPA) pBamCpa).getCacheManager();
  }

  @Override
  protected BackwardARGState computeCounterexampleSubgraph(
      final ARGReachedSet reachedSet,
      final Collection<BackwardARGState> newTreeTargets)
      throws MissingBlockException, InterruptedException {
    return computeCounterexampleSubgraph(reachedSet, newTreeTargets, Predicates.alwaysFalse());
  }

  protected BackwardARGState computeCounterexampleSubgraph(
      final ARGReachedSet reachedSet,
      final Collection<BackwardARGState> newTreeTargets,
      Predicate<ARGState> stop)
      throws MissingBlockException, InterruptedException {

    // start by creating ARGElements for each node needed in the tree
    final Map<ARGState, BackwardARGState> finishedStates = new HashMap<>();
    final NavigableSet<ARGState> waitlist = new TreeSet<>(); // for sorted IDs in ARGstates
    BackwardARGState root = null; // to be assigned later

    for (BackwardARGState newTreeTarget : newTreeTargets) {
      ARGState target = newTreeTarget.getARGState();
      finishedStates.put(target, newTreeTarget);
      waitlist.addAll(target.getParents()); // add parent for further processing
    }

    while (!waitlist.isEmpty()) {
      final ARGState currentState = waitlist.pollLast(); // get state with biggest ID

      if (finishedStates.containsKey(currentState)) {
        continue; // state already done
      }

      final BackwardARGState newCurrentState = new BackwardARGState(currentState);
      finishedStates.put(currentState, newCurrentState);

      if (stop.apply(currentState)) {
        return newCurrentState;
      }

      // add parent for further processing
      if (!currentState.getParents().isEmpty()) {
        waitlist.addAll(currentState.getParents());
      }

      final Set<BackwardARGState> childrenInSubgraph = new TreeSet<>();
      for (final ARGState child : currentState.getChildren()) {
        // if a child is not in the subgraph, it does not lead to the target, so ignore it.
        // Because of the ordering, all important children should be finished already.
        if (finishedStates.containsKey(child)) {
          childrenInSubgraph.add(finishedStates.get(child));
        }
      }

      NBAMExtendedState exCurrent = cache.extendedState(currentState);

      if (exCurrent.isBlockEntry()) {
        BlockEntry blockEntry = exCurrent.getBlockEntry();
        // Precision prec = reachedSet.asReachedSet().getPrecision(blockEntry.reducedState);

        NBAMCacheManager.Entry cacheEntry =
            (Entry) cache
                .get(blockEntry.reducedState, blockEntry.reducedPrecision, blockEntry.block);


        final Map<BackwardARGState, BackwardARGState> newExpandedToNewInnerTargets =
            new HashMap<>();
        for (final ARGState child : currentState.getChildren()) {
          BackwardARGState childInGraph = finishedStates.get(child);
          NBAMExtendedState exChild = cache.extendedState(child);
          BackwardARGState state =
              new BackwardARGState((ARGState) exChild.getBlockExit().reducedState);
          newExpandedToNewInnerTargets.put(childInGraph, state);
        }

        // Find out corresponding block in ARG and expand it:
        BackwardARGState newInnerRoot =
            computeCounterexampleSubgraph(
            reachedSet,
                newExpandedToNewInnerTargets.values(),
                s -> s.equals(cacheEntry.entryState));


        // reconnect ARG: replace the root of the inner block
        // with the existing state from the outer block with the current state,
        // then delete this node.
        for (ARGState innerChild : newInnerRoot.getChildren()) {
          innerChild.addParent(newCurrentState);
        }
        newInnerRoot.removeFromARG();

        // reconnect ARG: replace the target of the inner block
        // with the existing state from the outer block with the current state,
        // then delete this node.
        for (ARGState newExpandedTarget : childrenInSubgraph) {
          BackwardARGState newInnerTarget = newExpandedToNewInnerTargets.get(newExpandedTarget);
          for (ARGState innerParent : newInnerTarget.getParents()) {
            newExpandedTarget.addParent(innerParent);
          }
          newInnerTarget.removeFromARG();
        }

      } else {
        // children are a normal successors -> create an connection from parent to children
        for (final BackwardARGState newChild : childrenInSubgraph) {
          assert !currentState.getEdgesToChild(newChild.getARGState()).isEmpty() : String.format(
              "unexpected ARG state: parent has no edge to child: %s -/-> %s",
              currentState,
              newChild.getARGState());
          newChild.addParent(newCurrentState);
        }
      }

      if (currentState.getParents().isEmpty()) {
        assert root == null : "root should not be set before";
        assert waitlist.isEmpty() : "root should have the smallest ID";
        root = newCurrentState;
      }
    }
    assert root != null : "no root state found in reachedset with initial state "
        + reachedSet.asReachedSet().getFirstState();
    return root;
  }
}
