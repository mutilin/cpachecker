/*
 * CPAchecker is a tool for configurable software verification.
 *  This file is part of CPAchecker.
 *
 *  Copyright (C) 2007-2017  Dirk Beyer
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
package org.sosy_lab.cpachecker.cpa.rcucpa;

import java.util.Collection;
import org.sosy_lab.common.configuration.Configuration;
import org.sosy_lab.common.configuration.InvalidConfigurationException;
import org.sosy_lab.common.configuration.Option;
import org.sosy_lab.common.configuration.Options;
import org.sosy_lab.common.log.LogManager;
import org.sosy_lab.cpachecker.cfa.model.CFAEdge;
import org.sosy_lab.cpachecker.core.defaults.SingleEdgeTransferRelation;
import org.sosy_lab.cpachecker.core.interfaces.AbstractState;
import org.sosy_lab.cpachecker.core.interfaces.Precision;
import org.sosy_lab.cpachecker.exceptions.CPATransferException;

@Options(prefix = "cpa.rcucpa")
public class RCUTransfer extends SingleEdgeTransferRelation{

  private final String readLockName = "rcu_read_lock";
  private final String readUnlockName = "rcu_read_unlock";

  @Option(name = "fictReadLock", secure = true, description = "Name of a function marking a call "
      + "to a fictional read lock of RCU pointer")
  private String fictReadLock = "rlock_rcu";

  @Option(name = "fictReadUnlock", secure = true, description = "Name of a function marking a call "
      + "to a fictional read unlock of RCU pointer")
  private String fictReadUnlock = "runlock_rcu";

  @Option(name = "fictWriteLock", secure = true, description = "Name of a function marking a call "
      + "to a fictional write lock of RCU pointer")
  private String fictWriteLock = "wlock_rcu";

  @Option(name = "fictWriteUnlock", secure = true, description = "Name of a function marking a "
      + "call to a fictional write unlock of RCU pointer")
  private String fictWriteUnlock = "wunlock_rcu";

  private String sync = "synchronize_rcu";

  private final LogManager logger;

  public RCUTransfer(Configuration pConfig, LogManager pLogger) throws InvalidConfigurationException {
    logger = pLogger;
    pConfig.inject(this);
  }

  @Override
  public Collection<? extends AbstractState> getAbstractSuccessorsForEdge(
      AbstractState state, Precision precision, CFAEdge cfaEdge)
      throws CPATransferException, InterruptedException {
    return null;
  }
}
