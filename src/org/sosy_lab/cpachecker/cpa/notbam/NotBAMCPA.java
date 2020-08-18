package org.sosy_lab.cpachecker.cpa.notbam;

import java.nio.file.Path;
import java.nio.file.Paths;
import org.sosy_lab.common.configuration.ClassOption;
import org.sosy_lab.common.configuration.Configuration;
import org.sosy_lab.common.configuration.FileOption;
import org.sosy_lab.common.configuration.InvalidConfigurationException;
import org.sosy_lab.common.configuration.Option;
import org.sosy_lab.common.configuration.Options;
import org.sosy_lab.common.log.LogManager;
import org.sosy_lab.cpachecker.cfa.CFA;
import org.sosy_lab.cpachecker.cfa.blocks.BlockPartitioning;
import org.sosy_lab.cpachecker.cfa.blocks.BlockToDotWriter;
import org.sosy_lab.cpachecker.cfa.blocks.builder.BlockPartitioningBuilder;
import org.sosy_lab.cpachecker.cfa.blocks.builder.FunctionAndLoopPartitioning;
import org.sosy_lab.cpachecker.cfa.blocks.builder.PartitioningHeuristic;
import org.sosy_lab.cpachecker.core.defaults.AbstractSingleWrapperCPA;
import org.sosy_lab.cpachecker.core.defaults.AutomaticCPAFactory;
import org.sosy_lab.cpachecker.core.interfaces.CPAFactory;
import org.sosy_lab.cpachecker.core.interfaces.ConfigurableProgramAnalysis;
import org.sosy_lab.cpachecker.core.interfaces.ConfigurableProgramAnalysisWithBAM;
import org.sosy_lab.cpachecker.core.interfaces.Reducer;
import org.sosy_lab.cpachecker.core.interfaces.TransferRelation;
import org.sosy_lab.cpachecker.exceptions.CPAException;

@Options(prefix = "cpa.notbam")
public class NotBAMCPA extends AbstractSingleWrapperCPA {
  public static CPAFactory factory() {
    return AutomaticCPAFactory.forType(NotBAMCPA.class);
  }

  @Option(
      secure = true,
      description =
          "Type of partitioning (FunctionAndLoopPartitioning or DelayedFunctionAndLoopPartitioning)\n"
              + "or any class that implements a PartitioningHeuristic"
  )
  @ClassOption(packagePrefix = "org.sosy_lab.cpachecker.cfa.blocks.builder")
  private PartitioningHeuristic.Factory blockHeuristic = FunctionAndLoopPartitioning::new;

  @Option(secure = true, description = "export blocks")
  @FileOption(FileOption.Type.OUTPUT_FILE)
  private Path exportBlocksPath = Paths.get("block_cfa.dot");

  private LogManager logger;
  private BlockPartitioning partitioning;

  private Reducer reducer;
  private NBAMCacheManager cacheManager;
  private NBAMTransferRelation transferRelation;
  private NBAMPrecisionAdjustment precisionAdjustment;

  private NotBAMCPA(
      ConfigurableProgramAnalysis pCpa,
      Configuration config,
      LogManager pLogger,
      CFA pCfa) throws InvalidConfigurationException, CPAException
  {
    super(pCpa);
    config.inject(this);

    if (!(pCpa instanceof ConfigurableProgramAnalysisWithBAM)) {
      throw new IllegalArgumentException("Underlying analysis must be BAM capable");
    }

    ConfigurableProgramAnalysisWithBAM bamCpa = (ConfigurableProgramAnalysisWithBAM) pCpa;

    this.logger = pLogger;
    this.partitioning = buildPartitioning(pCfa, config);
    bamCpa.setPartitioning(partitioning);

    this.reducer = bamCpa.getReducer();
    this.cacheManager = new NBAMCacheManager(reducer);
    this.transferRelation = new NBAMTransferRelation(cacheManager, reducer,
        pCpa.getTransferRelation(), partitioning);
    this.precisionAdjustment = new NBAMPrecisionAdjustment(cacheManager,
        pCpa.getPrecisionAdjustment(), partitioning);
  }

  private BlockPartitioning buildPartitioning(CFA pCfa, Configuration pConfig) throws InvalidConfigurationException, CPAException {
    final BlockPartitioningBuilder blockBuilder = new BlockPartitioningBuilder();
    PartitioningHeuristic heuristic = blockHeuristic.create(logger, pCfa, pConfig);
    BlockPartitioning partitioning = heuristic.buildPartitioning(blockBuilder);
    if (exportBlocksPath != null) {
      BlockToDotWriter writer = new BlockToDotWriter(partitioning);
      writer.dump(exportBlocksPath, logger);
    }
    return partitioning;
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

  public Reducer getReducer() {
    return reducer;
  }

  public LogManager getLogger() {
    return logger;
  }
}
