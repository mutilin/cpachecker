/*
 *  CPAchecker is a tool for configurable software verification.
 *  This file is part of CPAchecker.
 *
 *  Copyright (C) 2007-2014  Dirk Beyer
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
package org.sosy_lab.cpachecker.cpa.bam.cache;

import com.google.common.collect.Collections2;
import java.util.Collection;
import org.sosy_lab.common.configuration.Configuration;
import org.sosy_lab.common.configuration.InvalidConfigurationException;
import org.sosy_lab.common.log.LogManager;
import org.sosy_lab.cpachecker.core.interfaces.Reducer;
import org.sosy_lab.cpachecker.core.reachedset.ReachedSet;

public class BAMCacheImpl extends AbstractBAMCache<ReachedSet> {

  public BAMCacheImpl(
      Configuration config,
      Reducer reducer,
      LogManager logger) throws InvalidConfigurationException {
    super(config, reducer, logger);
  }

  @Override
  public Collection<ReachedSet> getAllCachedReachedStates() {
    return Collections2.transform(preciseReachedCache.values(), BAMCacheEntry::getReachedSet);
  }


  @Override
  protected BAMCacheEntry getEntry(ReachedSet pElement) {
    return new BAMCacheEntry(pElement);
  }
}
