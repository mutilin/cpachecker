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

import com.google.common.base.Predicate;
import com.google.common.collect.ImmutableList;
import java.util.ArrayDeque;
import java.util.List;
import org.sosy_lab.cpachecker.core.interfaces.AbstractState;
import org.sosy_lab.cpachecker.core.interfaces.Precision;
import org.sosy_lab.cpachecker.core.reachedset.UnmodifiableReachedSet;
import org.sosy_lab.cpachecker.cpa.arg.ARGReachedSet;
import org.sosy_lab.cpachecker.cpa.arg.ARGState;
import org.sosy_lab.cpachecker.cpa.arg.path.ARGPath;
import org.sosy_lab.cpachecker.cpa.notbam.NBAMBasedRefiner.BackwardARGState;
import org.sosy_lab.cpachecker.cpa.notbam.NBAMCacheManager.Entry;
import org.sosy_lab.cpachecker.cpa.notbam.NBAMExtendedState.BlockEntry;

public class NBAMReachedSet extends ARGReachedSet.ForwardingARGReachedSet {
  private NotBAMCPA cpa;
  private ARGPath path;

  public NBAMReachedSet(ARGReachedSet pReached, NotBAMCPA pCpa, ARGPath pPath) {
    super(pReached);

    cpa = pCpa;
    path = pPath;
  }

  @Override
  public UnmodifiableReachedSet asReachedSet() {
    return new NBAMReachedSetView(path.getFirstState(), path.getLastState(),
        s -> super.asReachedSet().getPrecision(super.asReachedSet().getLastState()));
    // TODO do we really need the target-precision for refinements and not the actual one?
  }

  @Override
  public void removeSubtree(
      ARGState element, Precision newPrecision, Predicate<? super Precision> pPrecisionType)
      throws InterruptedException {
    removeSubtree(element, ImmutableList.of(newPrecision), ImmutableList.of(pPrecisionType));
  }

  @Override
  public void removeSubtree(ARGState state) throws InterruptedException {
    removeSubtree(state, ImmutableList.of(), ImmutableList.of());
  }

  @Override
  public void removeSubtree(
      ARGState pState, List<Precision> pPrecisions, List<Predicate<? super Precision>> pPrecTypes)
      throws InterruptedException {
    ARGState unwrapped = ((BackwardARGState) pState).getARGState();

    ArrayDeque<ARGState> states = new ArrayDeque<>();
    states.add(unwrapped);

    for (ARGState state: unwrapped.getParents()) {
      delegate.readdToWaitlist(state, pPrecisions, pPrecTypes);
    }

    while (!states.isEmpty()) {
      ARGState current = states.removeFirst();
      if (current instanceof BackwardARGState) {
        current = ((BackwardARGState) current).getARGState();
      }

      NBAMExtendedState exCurrent = cpa.getCacheManager().extendedState(current);

      for (ARGState c: current.getChildren()) {
        states.addFirst(c);
      }

      if (exCurrent.isBlockEntry()) {
        BlockEntry blockEntry = exCurrent.getBlockEntry();
        if (cpa.getCacheManager().isCached(blockEntry.block, blockEntry.reducedState,
            blockEntry.reducedPrecision))
        {
          Entry cacheEntry = cpa.getCacheManager().get(blockEntry.block, blockEntry.reducedState,
              blockEntry.reducedPrecision);

          if (!cacheEntry.getCacheUsages().isEmpty()) {
            for (ARGState c: cacheEntry.getCacheUsages()) {
              states.addFirst(c);
            }

            for (AbstractState state : cacheEntry.getCacheUsages()) {
              for (AbstractState parent : ((ARGState) state).getParents()) {
                delegate.readdToWaitlist(parent);
              }
            }

            cacheEntry.getCacheUsages().clear();
          }

          cacheEntry.getExitStates().clear();
        } else {
          System.out.println("Checking entry for state " + current.getStateId());
          System.out.println(" Block: hash=" + blockEntry.block.hashCode() + ", identity=" + System.identityHashCode(blockEntry.block));
          System.out.println(" Reduced state: hash=" + blockEntry.reducedState.hashCode() + ", identity=" + System.identityHashCode(blockEntry.reducedState));
          System.out.println(" Reduced precision: hash=" + blockEntry.reducedPrecision.hashCode() + ", identity=" + System.identityHashCode(blockEntry.reducedPrecision));
          System.out.println(" reducer.hashCode() = " + cpa.getReducer().getHashCodeForState(blockEntry.reducedState, blockEntry.reducedPrecision).hashCode());

          throw new RuntimeException("Block entry was not found in cache: state #" +
              current.getStateId() + ", block = " + blockEntry.block + ", reducedState = " +
              ((ARGState) blockEntry.reducedState).getStateId() + ", reducedPrecision = " +
              blockEntry.reducedPrecision);
        }
      }

      current.removeFromARG();
      delegate.remove(current);
    }
  }
}
