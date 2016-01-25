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
package org.sosy_lab.cpachecker.core;

import static com.google.common.collect.FluentIterable.from;
import static org.sosy_lab.common.ShutdownNotifier.interruptCurrentThreadOnShutdown;
import static org.sosy_lab.cpachecker.util.AbstractStates.IS_TARGET_STATE;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.logging.Level;

import javax.annotation.Nullable;

import org.sosy_lab.common.AbstractMBean;
import org.sosy_lab.common.ShutdownManager;
import org.sosy_lab.common.ShutdownNotifier;
import org.sosy_lab.common.ShutdownNotifier.ShutdownRequestListener;
import org.sosy_lab.common.configuration.Configuration;
import org.sosy_lab.common.configuration.InvalidConfigurationException;
import org.sosy_lab.common.configuration.Option;
import org.sosy_lab.common.configuration.Options;
import org.sosy_lab.common.io.Files;
import org.sosy_lab.common.io.Path;
import org.sosy_lab.common.io.Paths;
import org.sosy_lab.common.log.LogManager;
import org.sosy_lab.cpachecker.cfa.CFA;
import org.sosy_lab.cpachecker.cfa.CFACreator;
import org.sosy_lab.cpachecker.cfa.Language;
import org.sosy_lab.cpachecker.cfa.model.CFANode;
import org.sosy_lab.cpachecker.cfa.model.FunctionEntryNode;
import org.sosy_lab.cpachecker.cfa.model.FunctionExitNode;
import org.sosy_lab.cpachecker.core.CPAcheckerResult.Result;
import org.sosy_lab.cpachecker.core.CoreComponentsFactory.SpecAutomatonCompositionType;
import org.sosy_lab.cpachecker.core.algorithm.Algorithm;
import org.sosy_lab.cpachecker.core.algorithm.Algorithm.AlgorithmStatus;
import org.sosy_lab.cpachecker.core.algorithm.ExternalCBMCAlgorithm;
import org.sosy_lab.cpachecker.core.algorithm.impact.ImpactAlgorithm;
import org.sosy_lab.cpachecker.core.algorithm.mpa.MultiPropertyAlgorithm;
import org.sosy_lab.cpachecker.core.interfaces.AbstractState;
import org.sosy_lab.cpachecker.core.interfaces.ConfigurableProgramAnalysis;
import org.sosy_lab.cpachecker.core.interfaces.Precision;
import org.sosy_lab.cpachecker.core.interfaces.Property;
import org.sosy_lab.cpachecker.core.interfaces.PropertySummary;
import org.sosy_lab.cpachecker.core.interfaces.PropertySummaryExtractor;
import org.sosy_lab.cpachecker.core.interfaces.StateSpacePartition;
import org.sosy_lab.cpachecker.core.interfaces.Targetable;
import org.sosy_lab.cpachecker.core.reachedset.ReachedSet;
import org.sosy_lab.cpachecker.cpa.automaton.AutomatonState;
import org.sosy_lab.cpachecker.exceptions.CPAException;
import org.sosy_lab.cpachecker.exceptions.ParserException;
import org.sosy_lab.cpachecker.util.AbstractStates;
import org.sosy_lab.cpachecker.util.LoopStructure;
import org.sosy_lab.cpachecker.util.LoopStructure.Loop;
import org.sosy_lab.cpachecker.util.Pair;
import org.sosy_lab.cpachecker.util.automaton.TargetLocationProvider;
import org.sosy_lab.cpachecker.util.globalinfo.GlobalInfo;
import org.sosy_lab.cpachecker.util.statistics.Stats;
import org.sosy_lab.cpachecker.util.statistics.Stats.Contexts;

import com.google.common.base.Joiner;
import com.google.common.base.Optional;
import com.google.common.base.Preconditions;
import com.google.common.base.Splitter;
import com.google.common.base.StandardSystemProperty;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableCollection;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSet.Builder;
import com.google.common.collect.Sets;
import com.google.common.io.Resources;

@Options(prefix="analysis")
public class CPAchecker {

  public static interface CPAcheckerMXBean {
    public int getReachedSetSize();

    public void stop();
  }

  private static class CPAcheckerBean extends AbstractMBean implements CPAcheckerMXBean {

    private final ReachedSet reached;
    private final ShutdownManager shutdownManager;

    public CPAcheckerBean(ReachedSet pReached, LogManager logger, ShutdownManager pShutdownManager) {
      super("org.sosy_lab.cpachecker:type=CPAchecker", logger);
      reached = pReached;
      shutdownManager = pShutdownManager;
      register();
    }

    @Override
    public int getReachedSetSize() {
      return reached.size();
    }

    @Override
    public void stop() {
      shutdownManager.requestShutdown("A stop request was received via the JMX interface.");
    }

  }

  @Option(secure=true, description="stop after the first error has been found")
  private boolean stopAfterError = true;

  @Option(secure=true, name="disable",
      description="stop CPAchecker after startup (internal option, not intended for users)")
  private boolean disableAnalysis = false;

  public static enum InitialStatesFor {
    /**
     * Function entry node of the entry function
     */
    ENTRY,

    /**
     * Set of function entry nodes of all functions.
     */
    FUNCTION_ENTRIES,

    /**
     * All locations that are possible targets of the analysis.
     */
    TARGET,

    /**
     * Function exit node of the entry function.
     */
    EXIT,

    /**
     * All function exit nodes of all functions and all loop heads of endless loops.
     */
    FUNCTION_SINKS,

    /**
     * All function exit nodes of the entry function, and all loop heads of endless loops.
     */
    PROGRAM_SINKS
  }

  @Option(secure=true, name="initialStatesFor",
      description="What CFA nodes should be the starting point of the analysis?")
  private Set<InitialStatesFor> initialStatesFor = Sets.newHashSet(InitialStatesFor.ENTRY);

  @Option(secure=true,
      description="Partition the initial states based on the type of location they were created for (see 'initialStatesFor')")
  private boolean partitionInitialStates = false;

  @Option(secure=true, name="algorithm.CBMC",
      description="use CBMC as an external tool from CPAchecker")
  private boolean runCBMCasExternalTool = false;

  @Option(secure=true, description="Do not report unknown if analysis terminated, report true (UNSOUND!).")
  private boolean unknownAsTrue = false;

  private final LogManager logger;
  private final Configuration config;
  private final ShutdownManager shutdownManager;
  private final ShutdownNotifier shutdownNotifier;
  private final CoreComponentsFactory factory;

  // The content of this String is read from a file that is created by the
  // ant task "init".
  // To change the version, update the property in build.xml.
  private static final String version;
  static {
    String v = "(unknown version)";
    try {
      URL url = CPAchecker.class.getClassLoader().getResource("org/sosy_lab/cpachecker/VERSION.txt");
      if (url != null) {
        String content = Resources.toString(url, StandardCharsets.US_ASCII).trim();
        if (content.matches("[a-zA-Z0-9 ._+:-]+")) {
          v = content;
        }
      }
    } catch (IOException e) {
      // Ignore exception, no better idea what to do here.
    }
    version = v;
  }

  public static String getVersion() {
    return getCPAcheckerVersion()
        + " (" + StandardSystemProperty.JAVA_VM_NAME.value()
        +  " " + StandardSystemProperty.JAVA_VERSION.value() + ")";
  }

  public static String getCPAcheckerVersion() {
    return version;
  }

  public CPAchecker(Configuration pConfiguration, LogManager pLogManager,
      ShutdownManager pShutdownManager) throws InvalidConfigurationException {
    config = pConfiguration;
    logger = pLogManager;
    shutdownManager = pShutdownManager;
    shutdownNotifier = pShutdownManager.getNotifier();

    config.inject(this);
    factory = new CoreComponentsFactory(pConfiguration, pLogManager, shutdownNotifier);
  }

  public CPAcheckerResult run(String programDenotation) {

    logger.log(Level.INFO, "CPAchecker", getVersion(), "started");

    try (Contexts c = Stats.beginRootContext(getClass().getSimpleName())) {
      MainCPAStatistics stats = null;
      ReachedSet reached = null;
      Pair<Result, PropertySummary> result = Pair.of(Result.NOT_YET_STARTED, PropertySummary.UNKNOWN);

      final ShutdownRequestListener interruptThreadOnShutdown = interruptCurrentThreadOnShutdown();
      shutdownNotifier.register(interruptThreadOnShutdown);

      Algorithm algorithm = null;

      try {
        stats = new MainCPAStatistics(config, logger);

        // create reached set, cpa, algorithm
        stats.creationTime.start();
        reached = factory.createReachedSet();

        if (runCBMCasExternalTool) {

          checkIfOneValidFile(programDenotation);
          algorithm = new ExternalCBMCAlgorithm(programDenotation, config, logger);

        } else {
          CFA cfa = parse(programDenotation, stats);
          GlobalInfo.getInstance().storeCFA(cfa);
          shutdownNotifier.shutdownIfNecessary();

          final SpecAutomatonCompositionType speComposition =
              initialStatesFor.contains(InitialStatesFor.TARGET)
              ? SpecAutomatonCompositionType.BACKWARD_TO_ENTRY_SPEC
              : SpecAutomatonCompositionType.TARGET_SPEC;

          ConfigurableProgramAnalysis cpa = factory.createCPA(
              cfa, stats,
              speComposition);
          GlobalInfo.getInstance().setUpInfoFromCPA(cpa);

          algorithm = factory.createAlgorithm(cpa, programDenotation, cfa, stats);

          if (algorithm instanceof ImpactAlgorithm) {
            ImpactAlgorithm mcmillan = (ImpactAlgorithm)algorithm;
            reached.add(mcmillan.getInitialState(cfa.getMainFunction()), mcmillan.getInitialPrecision(cfa.getMainFunction()));
          } else {
            initializeReachedSet(reached, cpa, cfa.getMainFunction(), cfa);
          }
        }

        printConfigurationWarnings();

        stats.creationTime.stop();
        shutdownNotifier.shutdownIfNecessary();
        // now everything necessary has been instantiated

        if (disableAnalysis) {
          return new CPAcheckerResult(Result.NOT_YET_STARTED, PropertySummary.UNKNOWN, null, stats);
        }

        // run analysis
        AlgorithmStatus status = runAlgorithm(algorithm, reached, stats);
        result = extractResult(algorithm, reached, status);

      } catch (IOException e) {
        logger.logUserException(Level.SEVERE, e, "Could not read file");

      } catch (ParserException e) {
        logger.logUserException(Level.SEVERE, e, "Parsing failed");
        StringBuilder msg = new StringBuilder();
        msg.append("Please make sure that the code can be compiled by a compiler.\n");
        if (e.getLanguage() == Language.C) {
          msg.append("If the code was not preprocessed, please use a C preprocessor\nor specify the -preprocess command-line argument.\n");
        }
        msg.append("If the error still occurs, please send this error message\ntogether with the input file to cpachecker-users@googlegroups.com.\n");
        logger.log(Level.INFO, msg);

      } catch (InvalidConfigurationException e) {
        logger.logUserException(Level.SEVERE, e, "Invalid configuration");

      } catch (InterruptedException e) {
        // Get intermediate verification results
        // in case the analysis terminates
        // before it finished its work.
        result = extractResult(algorithm, reached, AlgorithmStatus.SOUND_BUT_INTERRUPTED);

        // CPAchecker must exit because it was asked to
        // we return normally instead of propagating the exception
        // so we can return the partial result we have so far
        if (!Strings.isNullOrEmpty(e.getMessage())) {
          logger.logUserException(Level.WARNING, e, "Analysis interrupted");
        }

      } catch (CPAException e) {
        logger.logUserException(Level.SEVERE, e, null);

      } finally {
        shutdownNotifier.unregister(interruptThreadOnShutdown);
      }

      return new CPAcheckerResult(result.getFirst(), result.getSecond(), reached, stats);
    }
  }


  private void checkIfOneValidFile(String fileDenotation) throws InvalidConfigurationException {
    if (!denotesOneFile(fileDenotation)) {
      throw new InvalidConfigurationException(
        "Exactly one code file has to be given.");
    }

    Path file = Paths.get(fileDenotation);

    try {
      Files.checkReadableFile(file);
    } catch (FileNotFoundException e) {
      throw new InvalidConfigurationException(e.getMessage());
    }
  }

  private boolean denotesOneFile(String programDenotation) {
    return !programDenotation.contains(",");
  }

  private CFA parse(String fileNamesCommaSeparated, MainCPAStatistics stats) throws InvalidConfigurationException, IOException,
      ParserException, InterruptedException {
    // parse file and create CFA
    CFACreator cfaCreator = new CFACreator(config, logger, shutdownNotifier);
    stats.setCFACreator(cfaCreator);

    Splitter commaSplitter = Splitter.on(',').omitEmptyStrings().trimResults();
    CFA cfa = cfaCreator.parseFileAndCreateCFA(commaSplitter.splitToList(fileNamesCommaSeparated));
    stats.setCFA(cfa);
    return cfa;
  }

  private void printConfigurationWarnings() {
    Set<String> unusedProperties = config.getUnusedProperties();
    if (!unusedProperties.isEmpty()) {
      logger.log(Level.WARNING, "The following configuration options were specified but are not used:\n",
          Joiner.on("\n ").join(unusedProperties), "\n");
    }
    Set<String> deprecatedProperties = config.getDeprecatedProperties();
    if (!deprecatedProperties.isEmpty()) {
      logger.log(Level.WARNING, "The following options are deprecated and will be removed in the future:\n",
          Joiner.on("\n ").join(deprecatedProperties), "\n");
    }
  }

  private AlgorithmStatus runAlgorithm(final Algorithm algorithm,
      final ReachedSet reached,
      final MainCPAStatistics stats) throws CPAException, InterruptedException {

    logger.log(Level.INFO, "Starting analysis ...");

    AlgorithmStatus status = AlgorithmStatus.SOUND_AND_PRECISE;

    // register management interface for CPAchecker
    CPAcheckerBean mxbean = new CPAcheckerBean(reached, logger, shutdownManager);

    stats.startAnalysisTimer();
    try {

      do {
        status = status.update(algorithm.run(reached));

        // either run only once (if stopAfterError == true)
        // or until the waitlist is empty
      } while (!stopAfterError && reached.hasWaitingState());

      logger.log(Level.INFO, "Stopping analysis ...");
      return status;

    } finally {
      stats.stopAnalysisTimer();

      // unregister management interface for CPAchecker
      mxbean.unregister();
    }
  }

  private Result analyzeResult(final ReachedSet reached, boolean isSound) {
    if (reached.hasWaitingState()) {
      logger.log(Level.WARNING, "Analysis not completed: there are still states to be processed.");
      return Result.UNKNOWN;
    }

    if (!isSound) {
      logger.log(Level.WARNING, "Analysis incomplete: no errors found, but not everything could be checked.");
      return Result.UNKNOWN;
    }

    return Result.TRUE;
  }

  private void addToInitialReachedSet(
      final Set<? extends CFANode> pLocations,
      final Object pPartitionKey,
      final ReachedSet pReached,
      final ConfigurableProgramAnalysis pCpa) {

    for (CFANode loc: pLocations) {
      StateSpacePartition putIntoPartition = partitionInitialStates
          ? StateSpacePartition.getPartitionWithKey(pPartitionKey)
          : StateSpacePartition.getDefaultPartition();

      AbstractState initialState = pCpa.getInitialState(loc, putIntoPartition);
      Precision initialPrecision = pCpa.getInitialPrecision(loc, putIntoPartition);

      pReached.add(initialState, initialPrecision);
    }
  }

  private void initializeReachedSet(
      final ReachedSet pReached,
      final ConfigurableProgramAnalysis pCpa,
      final FunctionEntryNode pAnalysisEntryFunction,
      final CFA pCfa) throws InvalidConfigurationException {

    logger.log(Level.FINE, "Creating initial reached set");

    for (InitialStatesFor isf: initialStatesFor) {
      final ImmutableSet<? extends CFANode> initialLocations;
      switch (isf) {
      case ENTRY:
        initialLocations = ImmutableSet.of(pAnalysisEntryFunction);
        break;
      case EXIT:
        initialLocations = ImmutableSet.of(pAnalysisEntryFunction.getExitNode());
        break;
      case FUNCTION_ENTRIES:
        initialLocations = ImmutableSet.copyOf(pCfa.getAllFunctionHeads());
        break;
      case FUNCTION_SINKS:
        initialLocations = ImmutableSet.<CFANode>builder().addAll(getAllEndlessLoopHeads(pCfa.getLoopStructure().get()))
                                                          .addAll(getAllFunctionExitNodes(pCfa))
                                                          .build();
        break;
      case PROGRAM_SINKS:
        Builder<CFANode> builder = ImmutableSet.<CFANode>builder().addAll(getAllEndlessLoopHeads(pCfa.getLoopStructure().get()));
        if (pCfa.getAllNodes().contains(pAnalysisEntryFunction.getExitNode())) {
          builder.add(pAnalysisEntryFunction.getExitNode());
        }
         initialLocations = builder.build();
        break;
      case TARGET:
        TargetLocationProvider tlp = new TargetLocationProvider(factory.getReachedSetFactory(), shutdownNotifier, logger, config, pCfa);
        initialLocations = tlp.tryGetAutomatonTargetLocations(pAnalysisEntryFunction);
        break;
      default:
        throw new AssertionError("Unhandled case statement: " + initialStatesFor);
      }

      addToInitialReachedSet(initialLocations, isf, pReached, pCpa);
    }

    if (!pReached.hasWaitingState()) {
      throw new InvalidConfigurationException("Initialization of the set of initial states failed: No analysis target found!");
    }

  }

  private Set<CFANode> getAllFunctionExitNodes(CFA cfa) {
    Set<CFANode> functionExitNodes = new HashSet<>();

    for (FunctionEntryNode node : cfa.getAllFunctionHeads()) {
      FunctionExitNode exitNode = node.getExitNode();
      if (cfa.getAllNodes().contains(exitNode)) {
        functionExitNodes.add(exitNode);
      }
    }
    return functionExitNodes;
  }

  private Set<CFANode> getAllEndlessLoopHeads(LoopStructure structure) {
    ImmutableCollection<Loop> loops = structure.getAllLoops();
    Set<CFANode> loopHeads = new HashSet<>();

    for (Loop l : loops) {
      if (l.getOutgoingEdges().isEmpty()) {
        // one loopHead per loop should be enough for finding all locations
        for (CFANode head : l.getLoopHeads()) {
          loopHeads.add(head);
        }
      }
    }
    return loopHeads;
  }

  private Pair<Result, PropertySummary> extractResult(
      @Nullable Algorithm pAlgorithm, ReachedSet reached, AlgorithmStatus status) {

    if (!GlobalInfo.getInstance().getCPA().isPresent()) {
      return Pair.of(Result.UNKNOWN, PropertySummary.UNKNOWN);
    }

    final PropertySummaryExtractor extractor;
    if (pAlgorithm instanceof MultiPropertyAlgorithm) {
      extractor = new MpaSummaryExtractor();
    } else {
      extractor = new DefaultSummaryExtractor();
    }

    PropertySummary summary = extractor.extractSummary(pAlgorithm, reached, status);

    Result verdict;

    if (summary.getViolatedProperties().isEmpty()) {

      if (status.isInterrupted()) {
        verdict = Result.UNKNOWN;
      } else {
        verdict = analyzeResult(reached, status.isSound());
        if (unknownAsTrue && verdict == Result.UNKNOWN) {
          verdict = Result.TRUE;
        }
      }

    } else {

      if (!status.isPrecise() || status.isInterrupted()) {
        verdict = Result.UNKNOWN;
      } else {
        verdict = Result.FALSE;
      }
    }

    return Pair.of(verdict, summary);
  }

  private class MpaSummaryExtractor implements PropertySummaryExtractor {

    @Override
    public PropertySummary extractSummary(Algorithm pAlgorithm, ReachedSet pReached, AlgorithmStatus pStatus) {

      Preconditions.checkArgument(pAlgorithm instanceof MultiPropertyAlgorithm);

      Optional<PropertySummary> result = ((MultiPropertyAlgorithm) pAlgorithm).getLastRunPropertySummary();
      if (result.isPresent()) {
        return result.get();
      } else {
        return PropertySummary.UNKNOWN;
      }
    }

  }

  private class DefaultSummaryExtractor implements PropertySummaryExtractor {

    private Set<Property> findViolatedProperties(final Algorithm pAlgorithm,
        final ReachedSet pReached) {

      final Set<Property> result = Sets.newHashSet();

      for (AbstractState e : from(pReached).filter(IS_TARGET_STATE).toList()) {
        Targetable t = (Targetable) e;
        result.addAll(t.getViolatedProperties());
      }

      return result;
    }

    private ImmutableSet<Property> findConsideredProperties(final Algorithm pAlgorithm,
        final ReachedSet pReached) {

      final Set<Property> result = Sets.newHashSet();

      AbstractState e = pReached.getFirstState();
      Collection<AutomatonState> automataStates = AbstractStates.extractStatesByType(e, AutomatonState.class);
      for (AutomatonState a: automataStates) {
        result.addAll(a.getOwningAutomaton().getEncodedProperties());
      }

      return ImmutableSet.copyOf(result);
    }


    @Override
    public PropertySummary extractSummary(Algorithm pAlgorithm, ReachedSet pReached, AlgorithmStatus pStatus) {

      final ImmutableSet<Property> violatedProperties = ImmutableSet.<Property>copyOf(findViolatedProperties(pAlgorithm, pReached));
      final ImmutableSet<Property> consideredProperties = findConsideredProperties(pAlgorithm, pReached);

      return new PropertySummary() {

        @Override
        public ImmutableSet<Property> getViolatedProperties() {
          return violatedProperties;
        }

        @Override
        public Optional<ImmutableSet<Property>> getUnknownProperties() {
          return Optional.absent();
        }

        @Override
        public Optional<ImmutableSet<Property>> getRelevantProperties() {
          return Optional.absent();
        }

        @Override
        public Optional<ImmutableSet<Property>> getSatisfiedProperties() {
          return Optional.absent();
        }

        @Override
        public ImmutableSet<Property> getConsideredProperties() {
          return consideredProperties;
        }
      };
    }

  }
}
