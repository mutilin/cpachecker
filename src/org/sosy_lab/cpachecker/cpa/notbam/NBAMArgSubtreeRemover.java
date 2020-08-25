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
import java.util.ArrayDeque;
import java.util.List;
import org.sosy_lab.cpachecker.core.interfaces.AbstractState;
import org.sosy_lab.cpachecker.core.interfaces.Precision;
import org.sosy_lab.cpachecker.cpa.arg.ARGReachedSet;
import org.sosy_lab.cpachecker.cpa.arg.ARGState;
import org.sosy_lab.cpachecker.cpa.arg.path.ARGPath;
import org.sosy_lab.cpachecker.cpa.bam.ARGSubtreeRemover;
import org.sosy_lab.cpachecker.cpa.bam.AbstractBAMCPA;
import org.sosy_lab.cpachecker.cpa.bam.BAMSubgraphComputer.BackwardARGState;
import org.sosy_lab.cpachecker.cpa.notbam.NBAMCacheManager.Entry;
import org.sosy_lab.cpachecker.cpa.notbam.NBAMExtendedState.BlockEntry;
import org.sosy_lab.cpachecker.util.statistics.ThreadSafeTimerContainer.TimerWrapper;

public class NBAMArgSubtreeRemover extends ARGSubtreeRemover {
  private final NBAMCacheManager cache;

  public NBAMArgSubtreeRemover(AbstractBAMCPA pBamCpa, TimerWrapper pRemoveCachedSubtreeTimer) {
    super(pBamCpa, pRemoveCachedSubtreeTimer);
    cache = ((NotBAMCPA) pBamCpa).getCacheManager();
  }

  @Override
  public void removeSubtree(
      ARGReachedSet pMainReachedSet,
      ARGPath pPath,
      ARGState pState,
      List<Precision> pNewPrecisions,
      List<Predicate<? super Precision>> pNewPrecisionTypes)
      throws InterruptedException {
    ARGState unwrapped = ((BackwardARGState) pState).getARGState();

    ArrayDeque<ARGState> states = new ArrayDeque<>();
    states.add(unwrapped);

    for (ARGState state : unwrapped.getParents()) {
      pMainReachedSet.readdToWaitlist(state, pNewPrecisions, pNewPrecisionTypes);
    }

    while (!states.isEmpty()) {
      ARGState current = states.removeFirst();
      if (current instanceof BackwardARGState) {
        current = ((BackwardARGState) current).getARGState();
      }

      NBAMExtendedState exCurrent = cache.extendedState(current);

      for (ARGState c : current.getChildren()) {
        states.addFirst(c);
      }

      if (exCurrent.isBlockEntry()) {
        BlockEntry blockEntry = exCurrent.getBlockEntry();
        if (cache
            .containsPreciseKey(
                blockEntry.reducedState,
                blockEntry.reducedPrecision,
                blockEntry.block)) {
          Entry cacheEntry =
              (Entry) cache
                  .get(blockEntry.reducedState, blockEntry.reducedPrecision, blockEntry.block);

          if (!cacheEntry.getCacheUsages().isEmpty()) {
            for (ARGState c : cacheEntry.getCacheUsages()) {
              states.addFirst(c);
            }

            for (AbstractState state : cacheEntry.getCacheUsages()) {
              for (AbstractState parent : ((ARGState) state).getParents()) {
                pMainReachedSet.readdToWaitlist(parent);
              }
            }

            cacheEntry.getCacheUsages().clear();
          }

          cacheEntry.getExitStates().clear();
        } else {

          throw new RuntimeException(
              "Block entry was not found in cache: state #"
                  + current.getStateId()
                  + ", block = "
                  + blockEntry.block
                  + ", reducedState = "
                  + ((ARGState) blockEntry.reducedState).getStateId()
                  + ", reducedPrecision = "
                  + blockEntry.reducedPrecision);
        }
      }

      current.removeFromARG();
      pMainReachedSet.remove(current);
    }
  }

}
