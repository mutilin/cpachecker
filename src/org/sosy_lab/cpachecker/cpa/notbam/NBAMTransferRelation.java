/*
 *  CPAchecker is a tool for configurable software verification.
 *  This file is part of CPAchecker.
 *
 *  Copyright (C) 2007-2019  Dirk Beyer
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
package org.sosy_lab.cpachecker.cpa.notbam;

import static org.sosy_lab.cpachecker.util.AbstractStates.extractLocation;

import com.google.common.collect.ImmutableCollection;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import javax.annotation.Nonnull;
import org.sosy_lab.cpachecker.cfa.blocks.Block;
import org.sosy_lab.cpachecker.cfa.blocks.BlockPartitioning;
import org.sosy_lab.cpachecker.cfa.model.CFAEdge;
import org.sosy_lab.cpachecker.cfa.model.CFANode;
import org.sosy_lab.cpachecker.core.interfaces.AbstractState;
import org.sosy_lab.cpachecker.core.interfaces.Precision;
import org.sosy_lab.cpachecker.core.interfaces.Reducer;
import org.sosy_lab.cpachecker.core.interfaces.TransferRelation;
import org.sosy_lab.cpachecker.core.reachedset.ReachedSet;
import org.sosy_lab.cpachecker.cpa.arg.ARGState;
import org.sosy_lab.cpachecker.cpa.notbam.NBAMExtendedState.BlockEntry;
import org.sosy_lab.cpachecker.cpa.notbam.NBAMExtendedState.BlockExit;
import org.sosy_lab.cpachecker.exceptions.CPATransferException;
import org.sosy_lab.cpachecker.util.Pair;

public class NBAMTransferRelation implements TransferRelation.ReachedSetAware {
  private NBAMCacheManager cacheManager;
  private Reducer reducer;
  private TransferRelation wrappedTransfer;
  private BlockPartitioning partitioning;
  private ReachedSet reachedSet;

  public NBAMTransferRelation(
      NBAMCacheManager pCacheManager,
      Reducer pReducer,
      TransferRelation pWrappedTransfer,
      BlockPartitioning pPartitioning)
  {
    cacheManager = pCacheManager;
    reducer = pReducer;
    wrappedTransfer = pWrappedTransfer;
    partitioning = pPartitioning;
  }

  @Override
  public void updateReachedSet(ReachedSet pReachedSet) {
    this.reachedSet = pReachedSet;
  }

  @Override
  public Collection<? extends AbstractState> getAbstractSuccessors(
      AbstractState state, Precision precision) throws CPATransferException, InterruptedException {
    if (reachedSet == null)
      throw new IllegalStateException("updateReachedSet() was not called before "
          + "getAbstractSuccessors()");

    final CFANode node = extractLocation(state);
    final ARGState argState = (ARGState) state;

    if (partitioning.isCallNode(node)) {
      Block block = partitioning.getBlockForCallNode(node);

      AbstractState reducedState = reducer.getVariableReducedState(state, block, node);
      Precision reducedPrecision = reducer.getVariableReducedPrecision(precision, block);

      // Always set entry information for block so we can reuse it later when we will restore ARG
      BlockEntry be = new BlockEntry(block, state, precision, reducedState, reducedPrecision);
      cacheManager.extendedState(state).setBlockEntry(be);

      if (cacheManager.hasCachedExits(block, reducedState, reducedPrecision)) {
//        System.out.println("Cache hit");
        NBAMCacheManager.Entry cacheEntry = cacheManager.get(block, reducedState, reducedPrecision);
        Set<AbstractState> expandedExits = new HashSet<>();

        cacheEntry.getCacheUsages().add(argState);

        for (Pair<AbstractState, Precision> p: cacheEntry.getExitStates()) {
          AbstractState rs = p.getFirstNotNull();
          Precision rp = p.getSecondNotNull();
          AbstractState expandedState = reducer.getVariableExpandedState(state, block, rs);
          Precision expandedPrecision = reducer.getVariableExpandedPrecision(precision, block, rp);

          ((ARGState) expandedState).addParent(argState);
          cacheManager.extendedState(expandedState).markAsAlreadyExpanded();
          cacheManager.extendedState(expandedState).setBlockExit(new BlockExit(block, rs,
              expandedPrecision));

          expandedExits.add(expandedState);
        }

        return expandedExits;
      } else {
        if (!cacheManager.isEmpty()) {
//          System.out.println("Cache miss");
        }

        cacheManager.create(block, reducedState, reducedPrecision, state);

        return getModifiedSuccessors(argState, reducedState, reducedPrecision);
      }
    }

    if (partitioning.isReturnNode(node) && !cacheManager.extendedState(state).isAlreadyExpanded()) {
      ARGState entryState = getEntryState(argState);

      if (!cacheManager.extendedState(entryState).isBlockEntry()) {
        throw new IllegalArgumentException("Got not block entry state when searching for parent:" +
            entryState);
      }

      BlockEntry blockEntry = cacheManager.extendedState(entryState).getBlockEntry();
      NBAMCacheManager.Entry cacheEntry = cacheManager.get(blockEntry.block,
          blockEntry.reducedState, blockEntry.reducedPrecision);

      // Attach new state to all existing cache usages:
      for (ARGState cu: cacheEntry.getCacheUsages()) {
        BlockEntry ce = cacheManager.extendedState(cu).getBlockEntry();

        AbstractState expandedState = reducer.getVariableExpandedState(ce.initialState,
            ce.block, state);

        Precision expandedPrecision = reducer.getVariableExpandedPrecision(ce.initialPrecision,
            ce.block, precision);

//        System.out.println("Attaching additional state to cache usage: " + ((ARGState) expandedState).getStateId());

        NBAMExtendedState richState = cacheManager.extendedState(expandedState);
        richState.markAsAlreadyExpanded();
        richState.setBlockExit(new BlockExit(ce.block, argState, expandedPrecision));

        ((ARGState) expandedState).addParent(cu);

        reachedSet.add(expandedState, expandedPrecision);
      }

      cacheEntry.addExitState(state, precision);

      AbstractState expandedState = reducer.getVariableExpandedState(blockEntry.initialState,
          blockEntry.block, state);

      Precision expandedPrecision = reducer.getVariableExpandedPrecision(blockEntry.initialPrecision,
          blockEntry.block, precision);

      NBAMExtendedState richState = cacheManager.extendedState(expandedState);
      richState.markAsAlreadyExpanded();
      richState.setBlockExit(new BlockExit(blockEntry.block, argState, expandedPrecision));

      return getModifiedSuccessors(argState, expandedState, expandedPrecision);
    }

    return wrappedTransfer.getAbstractSuccessors(state, precision);
  }

  @Nonnull
  private Collection<? extends AbstractState> getModifiedSuccessors(
      ARGState initialState,
      AbstractState modifiedState, Precision modifiedPrecision)
      throws CPATransferException, InterruptedException {
    Collection<? extends AbstractState> successors =
        wrappedTransfer.getAbstractSuccessors(modifiedState, modifiedPrecision);

    for (AbstractState s: successors) {
      ARGState as = (ARGState) s;

      cacheManager.extendedState(s).setModifiedPrecision(modifiedPrecision);

      as.removeParent((ARGState) modifiedState);
      as.addParent(initialState);
    }

    return successors;
  }

  @Override
  public Collection<? extends AbstractState> getAbstractSuccessorsForEdge(
      AbstractState state, Precision precision, CFAEdge cfaEdge)
      throws CPATransferException, InterruptedException
  {
    throw new UnsupportedOperationException("Should not be invoked on top-level CPAs");
  }

  /**
   * Walk ARG backwards until it finds correct call node for block specified by given exit node.
   */
  private ARGState getEntryState(ARGState exit) {
    ImmutableCollection<Block> blocksForReturnNode = partitioning.getBlocksForReturnNode(extractLocation(exit));
    if (blocksForReturnNode.size() != 1) {
      throw new UnsupportedOperationException("Multiple blocks at exit state is not yet suported");
    }

    Block exitBlock = blocksForReturnNode.iterator().next();

    ARGState entry = exit;
    do {
      entry = entry.getParents().iterator().next();
      CFANode entryLoc = extractLocation(entry);

      if (partitioning.isCallNode(entryLoc) && partitioning.getBlockForCallNode(entryLoc).equals(exitBlock)) {
        return entry;
      }
    } while (!entry.getParents().isEmpty());

    throw new RuntimeException("Could not find entry state in ARG for state " + exit +
        ", walked ARG up to " + entry);
  }
}
