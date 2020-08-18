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

import static com.google.common.base.Preconditions.checkArgument;

import com.google.common.collect.Lists;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nullable;
import org.sosy_lab.common.configuration.InvalidConfigurationException;
import org.sosy_lab.common.log.LogManager;
import org.sosy_lab.cpachecker.core.counterexample.CounterexampleInfo;
import org.sosy_lab.cpachecker.core.interfaces.ConfigurableProgramAnalysis;
import org.sosy_lab.cpachecker.core.interfaces.Refiner;
import org.sosy_lab.cpachecker.cpa.arg.ARGBasedRefiner;
import org.sosy_lab.cpachecker.cpa.arg.ARGCPA;
import org.sosy_lab.cpachecker.cpa.arg.ARGReachedSet;
import org.sosy_lab.cpachecker.cpa.arg.ARGState;
import org.sosy_lab.cpachecker.cpa.arg.AbstractARGBasedRefiner;
import org.sosy_lab.cpachecker.cpa.arg.path.ARGPath;
import org.sosy_lab.cpachecker.cpa.notbam.NBAMCacheManager.Entry;
import org.sosy_lab.cpachecker.cpa.notbam.NBAMExtendedState.BlockEntry;
import org.sosy_lab.cpachecker.exceptions.CPAException;
import org.sosy_lab.cpachecker.exceptions.CPATransferException;
import org.sosy_lab.cpachecker.util.CPAs;

public class NBAMBasedRefiner extends AbstractARGBasedRefiner {
  private NotBAMCPA cpa;
  private NBAMCacheManager cacheManager;

  public NBAMBasedRefiner(ARGBasedRefiner pRefiner, ARGCPA pArgCpa,
                          NotBAMCPA pBamCpa, LogManager pLogger)
  {
    super(pRefiner, pArgCpa, pLogger);

    this.cpa = pBamCpa;
    this.cacheManager = pBamCpa.getCacheManager();
  }

  /**
   * Create a {@link Refiner} instance that supports BAM from a {@link ARGBasedRefiner} instance.
   */
  public static Refiner forARGBasedRefiner(
      final ARGBasedRefiner pRefiner, final ConfigurableProgramAnalysis pCpa)
      throws InvalidConfigurationException {
    checkArgument(
        !(pRefiner instanceof Refiner),
        "ARGBasedRefiners may not implement Refiner, choose between these two!");

    if (!(pCpa instanceof NotBAMCPA)) {
      throw new InvalidConfigurationException("NBAM CPA needed for NBAM-based refinement");
    }
    NotBAMCPA bamCpa = (NotBAMCPA) pCpa;
    ARGCPA argCpa = CPAs.retrieveCPAOrFail(pCpa, ARGCPA.class, Refiner.class);
    return new NBAMBasedRefiner(pRefiner, argCpa, bamCpa, bamCpa.getLogger());
  }

  private void recurseBlock(List<ARGState> states, ARGState exitState, ARGState entryState) {
    ARGState current = exitState;

    while (!current.getParents().isEmpty() && !current.equals(entryState)) {
      ARGState parent = current.getParents().iterator().next();
      NBAMExtendedState exCurrent = cacheManager.extendedState(current);

      if (exCurrent.isBlockExit()) {
        NBAMExtendedState exParent = cacheManager.extendedState(parent);

        if (exParent.isBlockEntry()) {
          BlockEntry blockEntry = exParent.getBlockEntry();
          NBAMCacheManager.Entry cacheEntry = cacheManager
              .get(blockEntry.block, blockEntry.reducedState, blockEntry.reducedPrecision);

          // Find out corresponding block in ARG and expand it:
          recurseBlock(states, (ARGState) exCurrent.getBlockExit().reducedState,
              (ARGState) cacheEntry.entryState);
        } else {
          // It's already expanded block, copy as-is:
          states.add(new BackwardARGState(current));
        }
      } else {
        states.add(new BackwardARGState(current));
      }

      current = parent;
    }

    if (entryState != null && !current.equals(entryState)) {
      throw new IllegalArgumentException("Didn't find entryState when searching for a start of block from " + exitState.getStateId());
    }

    if (current.getParents().isEmpty()) {
      states.add(new BackwardARGState(current));
    }
  }

  @Nullable
  @Override
  protected ARGPath computePath(ARGState pLastElement, ARGReachedSet pReached)
      throws InterruptedException, CPATransferException
  {
    List<ARGState> states = new ArrayList<>();

    Utils.writeArg(pLastElement, Paths.get("output/refined.dot"), pLastElement);
    recurseBlock(states, pLastElement, null);

    // Wire ARG states to each other, keeping in mind that they are in reverse order:
    for (int i = 0; i < states.size() - 1; i++) {
      states.get(i).addParent(states.get(i + 1));
    }

    Utils.writeArg(states.get(0), Paths.get("output/refined_expanded.dot"));

    return new ARGPath(Lists.reverse(states));
  }

  @Override
  protected CounterexampleInfo performRefinementForPath(ARGReachedSet pReached, ARGPath pPath)
      throws CPAException, InterruptedException
  {
    assert pPath == null || pPath.size() > 0;

    if (pPath == null) {
      return CounterexampleInfo.spurious();
    } else {
      Utils.writeArg((ARGState) pReached.asReachedSet().getFirstState(), Paths.get("output/predicate.dot"));
      return super.performRefinementForPath(new NBAMReachedSet(pReached, cpa, pPath), pPath);
    }
  }

  static class BackwardARGState extends ARGState {
    private static final long serialVersionUID = -3279533907385516993L;

    public BackwardARGState(ARGState originalState) {
      super(originalState, null);
    }

    public ARGState getARGState() {
      return (ARGState) getWrappedState();
    }

    public BackwardARGState copy() {
      return new BackwardARGState(getARGState());
    }

    @Override
    public String toString() {
      return "BackwardARGState {{" + super.toString() + "}}";
    }
  }
}
