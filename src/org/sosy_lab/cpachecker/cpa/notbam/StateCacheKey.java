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

import java.util.Objects;
import org.sosy_lab.cpachecker.cfa.blocks.Block;
import org.sosy_lab.cpachecker.core.interfaces.AbstractState;
import org.sosy_lab.cpachecker.core.interfaces.Precision;
import org.sosy_lab.cpachecker.core.interfaces.Reducer;

public class StateCacheKey {
  public final Block block;
  public final AbstractState state;
  public final Precision precision;

  private Object stateHash;

  public StateCacheKey(
      Block pBlock,
      AbstractState pState,
      Precision pPrecision,
      Reducer pReducer) {
    block = pBlock;
    state = pState;
    precision = pPrecision;

    stateHash = pReducer.getHashCodeForState(pState, pPrecision);
  }

  @Override
  public boolean equals(Object pO) {
    if (this == pO) {
      return true;
    }
    if (pO == null || getClass() != pO.getClass()) {
      return false;
    }
    StateCacheKey that = (StateCacheKey) pO;
    return Objects.equals(block, that.block) &&
        Objects.equals(stateHash, that.stateHash);
  }

  @Override
  public int hashCode() {
    return Objects.hash(block, stateHash);
  }
}
