/*
 *  CPAchecker is a tool for configurable software verification.
 *  This file is part of CPAchecker.
 *
 *  Copyright (C) 2007-2020  Dirk Beyer
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
 */
package org.sosy_lab.cpachecker.core;

import static com.google.common.base.Preconditions.checkArgument;
import static org.sosy_lab.common.ShutdownNotifier.interruptCurrentThreadOnShutdown;

import com.google.common.base.Joiner;
import com.google.common.base.VerifyException;
import java.io.IOException;
import java.util.List;
import java.util.Set;
import java.util.logging.Level;
import org.sosy_lab.common.ShutdownManager;
import org.sosy_lab.common.ShutdownNotifier.ShutdownRequestListener;
import org.sosy_lab.common.configuration.Configuration;
import org.sosy_lab.common.configuration.InvalidConfigurationException;
import org.sosy_lab.common.log.LogManager;
import org.sosy_lab.cpachecker.cfa.CFA;
import org.sosy_lab.cpachecker.cfa.CFAMutator;
import org.sosy_lab.cpachecker.cfa.Language;
import org.sosy_lab.cpachecker.core.CPAcheckerResult.Result;
import org.sosy_lab.cpachecker.core.interfaces.ConfigurableProgramAnalysis;
import org.sosy_lab.cpachecker.exceptions.CPAException;
import org.sosy_lab.cpachecker.exceptions.ParserException;
import org.sosy_lab.cpachecker.util.CPAs;
import org.sosy_lab.cpachecker.util.SpecificationProperty;
import org.sosy_lab.cpachecker.util.globalinfo.GlobalInfo;

// @Options(prefix = "cfa.mutations")
public class CPAcheckerMutator extends CPAchecker {

  //  @Option(
  //      secure = true,
  //      name = "expectedResult",
  //      description = "Correct result of analysis. TRUE or FALSE with violated properties
  // string.")
  //  private Result expectedResult = Result.NOT_YET_STARTED;
  //
  //  @Option(
  //      secure = true,
  //      name = "expectedViolatedProperties",
  //      description = "Expected violated properties string.")
  //  private String expectedViolatedProerties = "";
  //
  //  private CPAcheckerResult correctResult =
  //      new CPAcheckerResult(expectedResult, expectedViolatedProerties, null, null, null);

  private CPAcheckerResult originalResult = null;
  private Throwable originalThrowable = null;

  private CFAMutator cfaMutator = new CFAMutator(config, logger, shutdownNotifier);

  public CPAcheckerMutator(
      Configuration pConfiguration, LogManager pLogManager, ShutdownManager pShutdownManager)
      throws InvalidConfigurationException {
    super(pConfiguration, pLogManager, pShutdownManager);

    if (runCBMCasExternalTool) {
      throw new InvalidConfigurationException(
          "Cannot use option 'analysis.algorithm.CBMC' along with "
              + "'cfa.mutations', because CFA will not be constructed.");
    }

    printConfigurationWarnings();
    // boolean hasUnused = !config.getUnusedProperties().isEmpty();
    // boolean hasDeprecated = !config.getDeprecatedProperties().isEmpty();

    // if (hasUnused) {
    //   throw new InvalidConfigurationException("Configuration has unused properties.");
    // }
  }

  @Override
  public CPAcheckerResult run(
      List<String> programDenotation, Set<SpecificationProperty> properties) {
    checkArgument(!programDenotation.isEmpty());

    logger.logf(Level.INFO, "%s (%s) started", getVersion(config), getJavaInformation());

    CPAcheckerResult currentResult = null;
    Throwable currentThrowable = null;

    MainCPAStatistics totalStats = null;
    CFA lastCFA = null;
    ConfigurableProgramAnalysis lastCPA = null;

    final ShutdownRequestListener interruptThreadOnShutdown = interruptCurrentThreadOnShutdown();
    shutdownNotifier.register(interruptThreadOnShutdown);

    try {
      totalStats = new MainCPAStatistics(config, logger, shutdownNotifier);
    } catch (InvalidConfigurationException e) {
      logger.logUserException(Level.SEVERE, e, "Invalid configuration");
      return new CPAcheckerResult(result, violatedPropertyDescription, reached, cfa, totalStats);
    }

    for (int mutationRound = 0; true; mutationRound++) {
      stats = null;
      algorithm = null;
      reached = null;
      cfa = null;
      result = Result.NOT_YET_STARTED;
      violatedPropertyDescription = "";

      currentResult = null;
      currentThrowable = null;

      logger.logf(Level.INFO, "Mutation round %d", mutationRound);
      try {
        stats = new MainCPAStatistics(config, logger, shutdownNotifier);

        // create reached set, cpa, algorithm
        totalStats.creationTime.start();
        stats.creationTime.start();

        reached = factory.createReachedSet();

        // instead of cfa=parse(programDenotation,stats); TODO serialisedFile?
        // stats.setCFACreator(cfaMutator);
        if (mutationRound == 0) {
          logger.logf(
              Level.INFO,
              "Parsing CFA from file(s) \"%s\"",
              Joiner.on(", ").join(programDenotation));
          cfa = cfaMutator.parseFileAndCreateCFA(programDenotation);
        } else {
          cfa = cfaMutator.parseFileAndCreateCFA(null);
        }
        stats.setCFA(cfa);

        if (cfa == null) {
          break;
        }
        lastCFA = cfa;

        GlobalInfo.getInstance().storeCFA(cfa);
        shutdownNotifier.shutdownIfNecessary();

        createAlgorithm(properties);

        stats.creationTime.stop();
        totalStats.creationTime.stop();
        shutdownNotifier.shutdownIfNecessary();

        // now everything necessary has been instantiated: run analysis
        // TODO retrieve time for analysis to totalStats
        runAnalysis();

      } catch (IOException e) {
        logger.logUserException(Level.SEVERE, e, "Could not read file");
        shutdownNotifier.unregister(interruptThreadOnShutdown);
        break;

      } catch (ParserException e) {
        logger.logUserException(Level.SEVERE, e, "Parsing failed");
        StringBuilder msg = new StringBuilder();
        msg.append("Please make sure that the code can be compiled by a compiler.\n");
        if (e.getLanguage() == Language.C) {
          msg.append(
              "If the code was not preprocessed, please use a C preprocessor\nor specify the -preprocess command-line argument.\n");
        }
        msg.append(
            "If the error still occurs, please send this error message\ntogether with the input file to cpachecker-users@googlegroups.com.\n");
        logger.log(Level.INFO, msg);
        shutdownNotifier.unregister(interruptThreadOnShutdown);
        break;

      } catch (InvalidConfigurationException e) {
        logger.logUserException(Level.SEVERE, e, "Invalid configuration");
        shutdownNotifier.unregister(interruptThreadOnShutdown);
        break;

      } catch (InterruptedException e) {
        // CPAchecker must exit because it was asked to
        // we return normally instead of propagating the exception
        // so we can return the partial result we have so far
        logger.logUserException(Level.WARNING, e, "Analysis interrupted");
        shutdownNotifier.unregister(interruptThreadOnShutdown);
        break;

      } catch (CPAException e) {
        logger.logUserException(Level.SEVERE, e, null);
        currentThrowable = e;

      } catch (VerifyException | AssertionError e) {
        for (final StackTraceElement ste : e.getStackTrace()) {
          if (ste.getClassName().contains("CFACreator")) {
            shutdownNotifier.unregister(interruptThreadOnShutdown);
            throw e;
          }
        }
        logger.logUserException(Level.SEVERE, e, null);
        currentThrowable = e;

      } finally {
        CPAs.closeIfPossible(algorithm, logger);
      }

      currentResult =
          new CPAcheckerResult(result, violatedPropertyDescription, reached, cfa, stats);
      compareResults(currentResult, currentThrowable);
    }

    shutdownNotifier.unregister(interruptThreadOnShutdown);

    if (currentResult != null) {
      // if loop ended not bc of exception // TODO if exception how it prints this.
      logger.log(Level.INFO, "Mutations ended.");
      logger.log(Level.INFO, "Verification result:");
      if (originalResult != null) {
        logger.log(Level.INFO, originalResult.getResultString());
      } else {
        logger.log(Level.INFO, "null result"); // TODO wha
      }
      if (originalThrowable != null) {
        logger.logUserException(Level.INFO, originalThrowable, null);
      }
    }

    cfaMutator.collectStatistics(totalStats.getSubStatistics());
    totalStats.setCFA(lastCFA);
    totalStats.setCPA(lastCPA);
    return new CPAcheckerResult(
        originalResult.getResult(), violatedPropertyDescription, reached, lastCFA, totalStats);
  }

  @SuppressWarnings("null")
  private void compareResults(CPAcheckerResult currentResult, Throwable currentThrowable) {
    // TODO in DD there are three results: success fail unknown,
    // if im tracking exception success = remained cfa is ok*, rollback
    // and unknown (any failure/exception other than original) = idk, rollback
    // TODO the * logic

    boolean differentResult = false;
    if (originalResult != null) {
      differentResult =
        originalResult.getResult() != currentResult.getResult()
            || (originalResult.getResult() == Result.FALSE
                && !originalResult.getResultString().equals(currentResult.getResultString()));
    }

    boolean ot = originalThrowable != null;
    boolean ct = currentThrowable != null;
    boolean differentThrowableClass =
        ot && ct && !originalThrowable.getClass().equals(currentThrowable.getClass());
    boolean differentThrowableMessages =
        ot && ct && !originalThrowable.getMessage().equals(currentThrowable.getMessage());

    if (originalResult == null) {
      // init originals
      originalResult = currentResult;
      originalThrowable = currentThrowable;
      logger.logf(Level.INFO, "original result: %s", originalResult.getResultString());
      if (originalThrowable != null) {
        logger.logf(Level.INFO, "original exception: %s", originalThrowable);
      }

    } else if (differentResult || ot != ct || differentThrowableClass) {
      // result changed
      logger.log(Level.INFO, "Result has changed, mutation rollback.");
      if (!ot) {
        logger.logf(Level.INFO, "Expected result: %s", originalResult.getResultString());
      } else {
        logger.logf(Level.INFO, "Expected exception: %s", originalThrowable.getMessage());
      }
      if (!ct) {
        logger.logf(Level.INFO, "Got result: %s", currentResult.getResultString());
      } else {
        logger.logf(Level.INFO, "Got exception: %s", currentThrowable.getMessage());
      }
      cfaMutator.rollback();

    } else if (differentThrowableMessages) {
      // error message changed
      logger.logf(
          Level.WARNING,
          "The result is considered unchanged, but error message is different:\noriginal:%s\ncurrent:%s",
          originalThrowable.getMessage(),
          currentThrowable.getMessage());

    } else {
      // result remained the same
      logger.log(Level.INFO, "Result has not changed.");
      logger.logf(Level.FINE, "Got %s", currentResult.getResultString());
      if (currentThrowable != null) {
        logger.logf(Level.FINE, "With %s", currentThrowable.getMessage());
      }
    }
  }
}
