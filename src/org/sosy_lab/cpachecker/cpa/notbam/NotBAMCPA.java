package org.sosy_lab.cpachecker.cpa.notbam;

import org.sosy_lab.common.ShutdownNotifier;
import org.sosy_lab.common.configuration.Configuration;
import org.sosy_lab.common.configuration.InvalidConfigurationException;
import org.sosy_lab.common.log.LogManager;
import org.sosy_lab.cpachecker.cfa.CFA;
import org.sosy_lab.cpachecker.core.Specification;
import org.sosy_lab.cpachecker.core.defaults.AutomaticCPAFactory;
import org.sosy_lab.cpachecker.core.interfaces.CPAFactory;
import org.sosy_lab.cpachecker.core.interfaces.ConfigurableProgramAnalysis;
import org.sosy_lab.cpachecker.core.interfaces.ConfigurableProgramAnalysisWithBAM;
import org.sosy_lab.cpachecker.core.interfaces.TransferRelation;
import org.sosy_lab.cpachecker.cpa.bam.AbstractBAMCPA;
import org.sosy_lab.cpachecker.cpa.bam.cache.BAMDataManager;
import org.sosy_lab.cpachecker.exceptions.CPAException;

public class NotBAMCPA extends AbstractBAMCPA {
  public static CPAFactory factory() {
    return AutomaticCPAFactory.forType(NotBAMCPA.class);
  }

  private NBAMCacheManager cacheManager;
  private NBAMTransferRelation transferRelation;
  private NBAMPrecisionAdjustment precisionAdjustment;

  private NotBAMCPA(
      ConfigurableProgramAnalysis pCpa,
      Configuration config,
      LogManager pLogger,
      ShutdownNotifier pNotifier,
      Specification pSpecification,
      CFA pCfa) throws InvalidConfigurationException, CPAException
  {
    super(pCpa, config, pLogger, pNotifier, pSpecification, pCfa);
    config.inject(this);

    if (!(pCpa instanceof ConfigurableProgramAnalysisWithBAM)) {
      throw new IllegalArgumentException("Underlying analysis must be BAM capable");
    }

    this.cacheManager = new NBAMCacheManager(getReducer());
    this.transferRelation =
        new NBAMTransferRelation(
            cacheManager,
            getReducer(),
        pCpa.getTransferRelation(),
        getBlockPartitioning());
    this.precisionAdjustment = new NBAMPrecisionAdjustment(cacheManager,
        pCpa.getPrecisionAdjustment(),
        getBlockPartitioning());
  }

  @Override
  public TransferRelation getTransferRelation() {
    return transferRelation;
  }

  @Override
  public NBAMPrecisionAdjustment getPrecisionAdjustment() {
    return precisionAdjustment;
  }

  public NBAMCacheManager getCacheManager() {
    return cacheManager;
  }

  @Override
  public BAMDataManager getData() {
    // TODO Auto-generated method stub
    return null;
  }
}
