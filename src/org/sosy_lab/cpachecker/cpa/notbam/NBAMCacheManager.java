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

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.sosy_lab.cpachecker.cfa.blocks.Block;
import org.sosy_lab.cpachecker.core.interfaces.AbstractState;
import org.sosy_lab.cpachecker.core.interfaces.Precision;
import org.sosy_lab.cpachecker.core.interfaces.Reducer;
import org.sosy_lab.cpachecker.cpa.arg.ARGState;
import org.sosy_lab.cpachecker.util.Pair;

public class NBAMCacheManager {
  private static class StateKey {
    final Block context;
    final Object stateHash;

    StateKey(Block pContext, Object pStateHash) {
      context = pContext;
      stateHash = pStateHash;
    }

    @Override
    public boolean equals(Object pO) {
      if (this == pO) {
        return true;
      }

      if (pO == null || getClass() != pO.getClass()) {
        return false;
      }

      StateKey stateKey = (StateKey) pO;
      return Objects.equals(context, stateKey.context) &&
          Objects.equals(stateHash, stateKey.stateHash);
    }

    @Override
    public int hashCode() {
      return Objects.hash(context, stateHash);
    }
  }


  public static class Entry {
    public final AbstractState entryState;
    private Set<Pair<AbstractState, Precision>> exitStates = new HashSet<>();
    private Set<ARGState> cacheUsages = new HashSet<>();

    private Entry(AbstractState pEntryState) {
      entryState = pEntryState;
    }

    public Set<Pair<AbstractState, Precision>> getExitStates() {
      return exitStates;
    }

    public void addExitState(AbstractState state, Precision precision) {
      exitStates.add(Pair.of(state, precision));
    }

    public Set<ARGState> getCacheUsages() {
      return cacheUsages;
    }
  }

  private Reducer reducer;
  private Map<StateKey, Entry> blockCache = new HashMap<>();
  private Map<ARGState, NBAMExtendedState> extendedStates = new HashMap<>();

  public NBAMCacheManager(Reducer pReducer) {
    reducer = pReducer;
  }

  public boolean isEmpty() {
    return blockCache.isEmpty();
  }

  public boolean isCached(Block block, AbstractState reducedState, Precision reducedPrecision) {
    StateKey mapKey = new StateKey(block, reducer.getHashCodeForState(reducedState, reducedPrecision));

    return blockCache.containsKey(mapKey);
  }

  public boolean hasCachedExits(Block block, AbstractState reducedState, Precision reducedPrecision) {
    StateKey mapKey = new StateKey(block, reducer.getHashCodeForState(reducedState, reducedPrecision));

    return blockCache.containsKey(mapKey) && !blockCache.get(mapKey).exitStates.isEmpty();
  }

  public Entry get(Block block, AbstractState reducedState, Precision reducedPrecision) {
    Entry ret = blockCache.get(new StateKey(block,
        reducer.getHashCodeForState(reducedState, reducedPrecision)));

    return Objects.requireNonNull(ret, "Cache entry does not exists");
  }

  public void create(Block block, AbstractState reducedState, Precision reducedPrecision,
                     AbstractState entryState)
  {
    blockCache.put(new StateKey(block,
        reducer.getHashCodeForState(reducedState, reducedPrecision)),
        new Entry(entryState));
  }

  public NBAMExtendedState extendedState(AbstractState state) {
    return extendedStates.computeIfAbsent((ARGState) state, NBAMExtendedState::new);
  }

  public void replaceState(AbstractState oldState, AbstractState newState) {
    NBAMExtendedState oldExt = extendedStates.remove((ARGState) oldState);
    if (oldExt != null) {
      // TODO: Is such replacement sufficient?
      extendedStates.put((ARGState) newState, new NBAMExtendedState((ARGState) newState, oldExt));
    }
  }
}
