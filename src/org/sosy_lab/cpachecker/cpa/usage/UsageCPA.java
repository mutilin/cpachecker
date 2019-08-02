/*
 *  CPAchecker is a tool for configurable software verification.
 *  This file is part of CPAchecker.
 *
 *  Copyright (C) 2007-2012  Dirk Beyer
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
package org.sosy_lab.cpachecker.cpa.usage;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collection;
import java.util.Map;
import org.sosy_lab.common.ShutdownNotifier;
import org.sosy_lab.common.configuration.Configuration;
import org.sosy_lab.common.configuration.FileOption;
import org.sosy_lab.common.configuration.FileOption.Type;
import org.sosy_lab.common.configuration.InvalidConfigurationException;
import org.sosy_lab.common.configuration.Option;
import org.sosy_lab.common.configuration.Options;
import org.sosy_lab.common.log.LogManager;
import org.sosy_lab.cpachecker.cfa.CFA;
import org.sosy_lab.cpachecker.cfa.blocks.BlockPartitioning;
import org.sosy_lab.cpachecker.cfa.model.CFANode;
import org.sosy_lab.cpachecker.core.defaults.AbstractSingleWrapperCPA;
import org.sosy_lab.cpachecker.core.defaults.AutomaticCPAFactory;
import org.sosy_lab.cpachecker.core.defaults.DelegateAbstractDomain;
import org.sosy_lab.cpachecker.core.interfaces.AbstractDomain;
import org.sosy_lab.cpachecker.core.interfaces.AbstractState;
import org.sosy_lab.cpachecker.core.interfaces.ApplyOperator;
import org.sosy_lab.cpachecker.core.interfaces.CPAFactory;
import org.sosy_lab.cpachecker.core.interfaces.ConfigurableProgramAnalysis;
import org.sosy_lab.cpachecker.core.interfaces.ConfigurableProgramAnalysisTM;
import org.sosy_lab.cpachecker.core.interfaces.ConfigurableProgramAnalysisWithBAM;
import org.sosy_lab.cpachecker.core.interfaces.MergeOperator;
import org.sosy_lab.cpachecker.core.interfaces.Precision;
import org.sosy_lab.cpachecker.core.interfaces.PrecisionAdjustment;
import org.sosy_lab.cpachecker.core.interfaces.Reducer;
import org.sosy_lab.cpachecker.core.interfaces.StateSpacePartition;
import org.sosy_lab.cpachecker.core.interfaces.Statistics;
import org.sosy_lab.cpachecker.core.interfaces.StatisticsProvider;
import org.sosy_lab.cpachecker.core.interfaces.StopOperator;
import org.sosy_lab.cpachecker.core.interfaces.TransferRelation;
import org.sosy_lab.cpachecker.cpa.local.LocalState.DataType;
import org.sosy_lab.cpachecker.cpa.lock.LockCPA;
import org.sosy_lab.cpachecker.cpa.lock.LockTransferRelation;
import org.sosy_lab.cpachecker.util.CPAs;
import org.sosy_lab.cpachecker.util.identifiers.GeneralIdentifier;

@Options
public class UsageCPA extends AbstractSingleWrapperCPA
    implements ConfigurableProgramAnalysisWithBAM, ConfigurableProgramAnalysisTM,
    StatisticsProvider {

  private final StopOperator stopOperator;
  private final MergeOperator mergeOperator;
  private final UsageTransferRelation transferRelation;
  private final PrecisionAdjustment precisionAdjustment;
  private final ShutdownNotifier shutdownNotifier;
  private final UsageCPAStatistics statistics;
  private final CFA cfa;
  private final Configuration config;
  private final LogManager logger;
  private final Map<CFANode, Map<GeneralIdentifier, DataType>> localMap;
  private final UsageProcessor usageProcessor;

  public static CPAFactory factory() {
    return AutomaticCPAFactory.forType(UsageCPA.class);
  }

  @Option(description = "A path to precision", name = "precision.path", secure = true)
  @FileOption(Type.OUTPUT_FILE)
  private Path outputFileName = Paths.get("localsave");

  private UsageCPA(
      ConfigurableProgramAnalysis pCpa,
      CFA pCfa,
      ShutdownNotifier pShutdownNotifier,
      LogManager pLogger,
      Configuration pConfig)
      throws InvalidConfigurationException {
    super(pCpa);
    pConfig.inject(this);
    config = pConfig;
    this.cfa = pCfa;
    LockCPA lockCPA = CPAs.retrieveCPA(this, LockCPA.class);
    this.statistics =
        new UsageCPAStatistics(
            pConfig,
            pLogger,
            pCfa,
            lockCPA != null ? (LockTransferRelation) lockCPA.getTransferRelation() : null);
    this.stopOperator = new UsageStopOperator(pCpa.getStopOperator(), statistics);
    this.mergeOperator = new UsageMergeOperator(pCpa.getMergeOperator(), statistics);

    this.precisionAdjustment =
        new UsagePrecisionAdjustment(pCpa.getPrecisionAdjustment(), statistics);
    logger = pLogger;
    this.transferRelation =
        new UsageTransferRelation(pCpa.getTransferRelation(), pConfig, pLogger, statistics);

    PresisionParser parser = new PresisionParser(cfa, logger);
    localMap = parser.parse(outputFileName);
    usageProcessor =
        new UsageProcessor(
            pConfig,
            logger,
            localMap,
            transferRelation.getBinderFunctionInfo());
    shutdownNotifier = pShutdownNotifier;
  }

  @Override
  public AbstractDomain getAbstractDomain() {
    return DelegateAbstractDomain.<UsageState>getInstance();
  }

  @Override
  public TransferRelation getTransferRelation() {
    return transferRelation;
  }

  @Override
  public MergeOperator getMergeOperator() {
    return mergeOperator;
  }

  @Override
  public StopOperator getStopOperator() {
    return stopOperator;
  }

  @Override
  public PrecisionAdjustment getPrecisionAdjustment() {
    return precisionAdjustment;
  }

  @Override
  public Precision getInitialPrecision(CFANode pNode, StateSpacePartition p)
      throws InterruptedException {
    return getWrappedCpa().getInitialPrecision(pNode, p);
  }

  @Override
  public Reducer getReducer() throws InvalidConfigurationException {
    Reducer wrappedReducer = ((ConfigurableProgramAnalysisWithBAM) getWrappedCpa()).getReducer();
    return new UsageReducer(wrappedReducer);
  }

  @Override
  public void collectStatistics(Collection<Statistics> pStatsCollection) {
    pStatsCollection.add(statistics);
    super.collectStatistics(pStatsCollection);
  }

  public UsageCPAStatistics getStats() {
    return statistics;
  }

  public LogManager getLogger() {
    return logger;
  }

  @Override
  public AbstractState getInitialState(CFANode pNode, StateSpacePartition pPartition)
      throws InterruptedException {
    return UsageState.createInitialState(getWrappedCpa().getInitialState(pNode, pPartition));
  }

  @Override
  public void setPartitioning(BlockPartitioning pPartitioning) {
    ConfigurableProgramAnalysis cpa = getWrappedCpa();
    assert cpa instanceof ConfigurableProgramAnalysisWithBAM;
    ((ConfigurableProgramAnalysisWithBAM) cpa).setPartitioning(pPartitioning);
  }

  public UsageProcessor getUsageProcessor() {
    return usageProcessor;
  }

  public ShutdownNotifier getNotifier() {
    return shutdownNotifier;
  }

  public Configuration getConfig() {
    return config;
  }

  @Override
  public ApplyOperator getApplyOperator() {
    return new UsageApplyOperator(
        ((ConfigurableProgramAnalysisTM) getWrappedCpa()).getApplyOperator());
  }
}
