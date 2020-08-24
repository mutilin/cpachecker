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

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.sosy_lab.common.configuration.Configuration;
import org.sosy_lab.common.configuration.InvalidConfigurationException;
import org.sosy_lab.common.log.LogManager;
import org.sosy_lab.cpachecker.cfa.blocks.Block;
import org.sosy_lab.cpachecker.core.interfaces.AbstractState;
import org.sosy_lab.cpachecker.core.interfaces.Precision;
import org.sosy_lab.cpachecker.core.interfaces.Reducer;
import org.sosy_lab.cpachecker.core.reachedset.ReachedSet;
import org.sosy_lab.cpachecker.cpa.arg.ARGState;
import org.sosy_lab.cpachecker.cpa.bam.cache.AbstractBAMCache;
import org.sosy_lab.cpachecker.util.Pair;

public class NBAMCacheManager extends AbstractBAMCache<AbstractState> {

  public static class Entry extends BAMCacheEntry {
    public final AbstractState entryState;
    private Set<Pair<AbstractState, Precision>> exitStates = new HashSet<>();
    private Set<ARGState> cacheUsages = new HashSet<>();

    private Entry(AbstractState pEntryState) {
      super(null);
      entryState = pEntryState;
    }

    @Override
    public @Nullable List<AbstractState> getExitStates() {
      return exitStates.stream().map(p -> p.getFirst()).collect(Collectors.toList());
    }

    public Set<Pair<AbstractState, Precision>> getExitStatesAndPrecision() {
      return exitStates;
    }

    public void addExitState(AbstractState state, Precision precision) {
      exitStates.add(Pair.of(state, precision));
    }

    public Set<ARGState> getCacheUsages() {
      return cacheUsages;
    }
  }

  private Map<ARGState, NBAMExtendedState> extendedStates = new HashMap<>();

  public NBAMCacheManager(Configuration pConfig, Reducer pReducer, LogManager pLogger)
      throws InvalidConfigurationException {
    super(pConfig, pReducer, pLogger);
  }

  public boolean hasCachedExits(Block block, AbstractState reducedState, Precision reducedPrecision) {
    if (containsPreciseKey(reducedState, reducedPrecision, block)) {
      return get(reducedState, reducedPrecision, block).getExitStates().isEmpty();
    }
    return false;
  }

  public NBAMExtendedState extendedState(AbstractState state) {
    return extendedStates.computeIfAbsent((ARGState) state, NBAMExtendedState::new);
  }

  public void replaceState(AbstractState oldState, AbstractState newState) {
    NBAMExtendedState oldExt = extendedStates.remove(oldState);
    if (oldExt != null) {
      // TODO: Is such replacement sufficient?
      extendedStates.put((ARGState) newState, new NBAMExtendedState((ARGState) newState, oldExt));
    }
  }

  @Override
  protected BAMCacheEntry getEntry(AbstractState entryState) {
    return new Entry(entryState);
  }

  @Override
  public Collection<ReachedSet> getAllCachedReachedStates() {
    // TODO Auto-generated method stub
    return null;
  }
}
