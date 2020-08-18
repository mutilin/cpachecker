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

import org.sosy_lab.cpachecker.cfa.blocks.Block;
import org.sosy_lab.cpachecker.core.interfaces.AbstractState;
import org.sosy_lab.cpachecker.core.interfaces.Precision;
import org.sosy_lab.cpachecker.cpa.arg.ARGState;

public class NBAMExtendedState {
  public static class BlockEntry {
    public final Block block; // block + reduced info is needed for cache lookups
    public final AbstractState initialState;
    public final Precision initialPrecision;
    public final AbstractState reducedState;
    public final Precision reducedPrecision;

    public BlockEntry(Block pBlock, AbstractState pInitialState, Precision pInitialPrecision,
        AbstractState pReducedState, Precision pReducedPrecision)
    {
      block = pBlock;
      initialState = pInitialState;
      initialPrecision = pInitialPrecision;
      reducedState = pReducedState;
      reducedPrecision = pReducedPrecision;
    }
  }

  public static class BlockExit {
    public final Block block;
    public final AbstractState reducedState;
    public final Precision expandedPrecision;

    public BlockExit(Block pBlock, AbstractState pReducedState, Precision pExpandedPrecision) {
      block = pBlock;
      reducedState = pReducedState;
      expandedPrecision = pExpandedPrecision;
    }
  }

  public final ARGState state;

  private boolean alreadyExpanded = false;
  private BlockEntry blockEntry = null;
  private BlockExit blockExit = null;
  private Precision expandedPrecision;

  public NBAMExtendedState(ARGState pState) {
    state = pState;
  }

  public NBAMExtendedState(ARGState pState, NBAMExtendedState otherState) {
    state = pState;

    alreadyExpanded = otherState.alreadyExpanded;
    blockEntry = otherState.blockEntry;
    expandedPrecision = otherState.expandedPrecision;
  }

  public boolean isAlreadyExpanded() {
    return alreadyExpanded;
  }

  public void markAsAlreadyExpanded() {
    alreadyExpanded = true;
  }

  public boolean isBlockEntry() {
    return blockEntry != null;
  }

  public BlockEntry getBlockEntry() {
    return blockEntry;
  }

  public void setBlockEntry(BlockEntry pBlockEntry) {
    blockEntry = pBlockEntry;
  }

  public boolean isBlockExit() { return blockExit != null; }

  public BlockExit getBlockExit() {
    return blockExit;
  }

  public void setBlockExit(BlockExit pBlockExit) {
    blockExit = pBlockExit;
  }

  public Precision getExpandedPrecision() {
    return expandedPrecision;
  }

  public void setModifiedPrecision(Precision pExpandedPrecision) {
    expandedPrecision = pExpandedPrecision;
  }
}
