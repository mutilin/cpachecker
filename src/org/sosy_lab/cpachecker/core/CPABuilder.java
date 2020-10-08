// This file is part of CPAchecker,
// a tool for configurable software verification:
// https://cpachecker.sosy-lab.org
//
// SPDX-FileCopyrightText: 2007-2020 Dirk Beyer <https://www.sosy-lab.org>
//
// SPDX-License-Identifier: Apache-2.0

package org.sosy_lab.cpachecker.core;

import com.google.common.base.Preconditions;
import com.google.common.base.Splitter;
import com.google.common.base.Throwables;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.logging.Level;
import org.sosy_lab.common.Classes;
import org.sosy_lab.common.Classes.UnexpectedCheckedException;
import org.sosy_lab.common.ShutdownNotifier;
import org.sosy_lab.common.configuration.Configuration;
import org.sosy_lab.common.configuration.InvalidConfigurationException;
import org.sosy_lab.common.configuration.Option;
import org.sosy_lab.common.configuration.Options;
import org.sosy_lab.common.log.LogManager;
import org.sosy_lab.cpachecker.cfa.CFA;
import org.sosy_lab.cpachecker.core.interfaces.CPAFactory;
import org.sosy_lab.cpachecker.core.interfaces.ConfigurableProgramAnalysis;
import org.sosy_lab.cpachecker.core.reachedset.AggregatedReachedSets;
import org.sosy_lab.cpachecker.core.reachedset.ReachedSetFactory;
import org.sosy_lab.cpachecker.core.specification.Specification;
import org.sosy_lab.cpachecker.cpa.automaton.Automaton;
import org.sosy_lab.cpachecker.cpa.automaton.ControlAutomatonCPA;
import org.sosy_lab.cpachecker.cpa.composite.CompositeCPA;
import org.sosy_lab.cpachecker.cpa.location.LocationCPA;
import org.sosy_lab.cpachecker.exceptions.CPAException;
import org.sosy_lab.cpachecker.exceptions.InvalidComponentException;

@Options
public class CPABuilder {

  private static final String CPA_OPTION_NAME = "cpa";
  private static final String CPA_CLASS_PREFIX = "org.sosy_lab.cpachecker";

  private static final Splitter LIST_SPLITTER = Splitter.on(',').trimResults().omitEmptyStrings();

  @Option(secure=true, name=CPA_OPTION_NAME,
      description="CPA to use (see doc/Configuration.md for more documentation on this)")
  private String cpaName = CompositeCPA.class.getCanonicalName();

  private final Configuration config;
  private final LogManager logger;
  private final ShutdownNotifier shutdownNotifier;
  private final ReachedSetFactory reachedSetFactory;

  public CPABuilder(Configuration pConfig, LogManager pLogger, ShutdownNotifier pShutdownNotifier,
      ReachedSetFactory pReachedSetFactory) throws InvalidConfigurationException {
    this.config = pConfig;
    this.logger = pLogger;
    this.shutdownNotifier = pShutdownNotifier;
    this.reachedSetFactory = pReachedSetFactory;
    config.inject(this);
  }

  public ConfigurableProgramAnalysis buildCPAs(
      final CFA cfa,
      final Specification specification,
      AggregatedReachedSets pAggregatedReachedSets)
      throws InvalidConfigurationException, CPAException {
    return buildCPAs(cfa, specification, ImmutableList.of(), pAggregatedReachedSets);
  }

  public ConfigurableProgramAnalysis buildCPAs(
      final CFA cfa,
      final Specification specification,
      final List<Automaton> additionalAutomata,
      AggregatedReachedSets pAggregatedReachedSets)
      throws InvalidConfigurationException, CPAException {
    Set<String> usedAliases = new HashSet<>();

    List<Automaton> specAutomata = specification.getSpecificationAutomata();
    List<ConfigurableProgramAnalysis> cpas =
        new ArrayList<>(specAutomata.size() + additionalAutomata.size());

    for (Automaton automaton : Iterables.concat(specAutomata, additionalAutomata)) {
      String cpaAlias = automaton.getName();

      if (!usedAliases.add(cpaAlias)) {
        throw new InvalidConfigurationException(
            "Name " + cpaAlias + " used twice for an automaton.");
      }

      CPAFactory factory = ControlAutomatonCPA.factory();
      factory.setConfiguration(Configuration.copyWithNewPrefix(config, cpaAlias));
      factory.setLogger(logger.withComponentName(cpaAlias));
      factory.set(cfa, CFA.class);
      factory.set(pAggregatedReachedSets, AggregatedReachedSets.class);
      factory.set(automaton, Automaton.class);
      factory.setShutdownNotifier(shutdownNotifier);

      cpas.add(factory.createInstance());
    }

    ConfigurableProgramAnalysis cpa =
        buildCPAs(
        cpaName, CPA_OPTION_NAME, usedAliases, cpas, cfa, specification, pAggregatedReachedSets);
    if (!cpas.isEmpty()) {
      throw new InvalidConfigurationException(
          "Option specification gave specification automata, but no CompositeCPA was used");
    }
    return cpa;
  }

  private ConfigurableProgramAnalysis buildCPAs(
      String optionValue,
      String optionName,
      Set<String> usedAliases,
      List<ConfigurableProgramAnalysis> cpas,
      final CFA cfa,
      final Specification specification,
      AggregatedReachedSets pAggregatedReachedSets)
      throws InvalidConfigurationException, CPAException {
    Preconditions.checkNotNull(optionValue);

    // parse option (may be of syntax "classname alias"
    List<String> optionParts = Splitter.onPattern("\\s+").splitToList(optionValue.trim());
    String cpaNameFromOption = optionParts.get(0);
    String cpaAlias = getCPAAlias(optionValue, optionName, optionParts, cpaNameFromOption);

    if (!usedAliases.add(cpaAlias)) {
      throw new InvalidConfigurationException("Alias " + cpaAlias + " used twice for a CPA.");
    }

    // shortcut for a ControlAutomatonCPA which was already instantiated, but is wrapped in a
    // cpa other than CompositeCPA (such as e.g. an AbstractSingleWrapperCPA)
    if (cpaAlias.equals(ControlAutomatonCPA.class.getSimpleName())) {
      Optional<ConfigurableProgramAnalysis> first =
          cpas.stream().filter(x -> x instanceof ControlAutomatonCPA).findFirst();
      if (first.isPresent()) {
        ConfigurableProgramAnalysis cpa = first.orElseThrow();
        cpas.remove(cpa);
        return cpa;
      }
    }

    // first get instance of appropriate factory

    Class<?> cpaClass = getCPAClass(optionName, cpaNameFromOption);

    logger.log(Level.FINER, "Instantiating CPA " + cpaClass.getName() + " with alias " + cpaAlias);

    CPAFactory factory = getFactoryInstance(cpaNameFromOption, cpaClass);

    // now use factory to get an instance of the CPA

    factory.setConfiguration(Configuration.copyWithNewPrefix(config, cpaAlias));
    factory.setLogger(logger.withComponentName(cpaAlias));
    factory.setShutdownNotifier(shutdownNotifier);
    factory.set(pAggregatedReachedSets, AggregatedReachedSets.class);
    factory.set(specification, Specification.class);
    if (reachedSetFactory != null) {
      factory.set(reachedSetFactory, ReachedSetFactory.class);
    }
    if (cfa != null) {
      factory.set(cfa, CFA.class);
    }

    boolean hasChildren =
        createAndSetChildrenCPAs(
            cpaNameFromOption,
            cpaAlias,
            factory,
            usedAliases,
            cpas,
            cfa,
            specification,
            pAggregatedReachedSets);

    if (optionName.equals(CPA_OPTION_NAME)
        && cpaClass.equals(CompositeCPA.class)
        && !hasChildren) {
      // This is the top-level CompositeCPA that is the default,
      // but without any children. This means that the user did not specify any
      // meaningful configuration.
      throw new InvalidConfigurationException(
          "Please specify a configuration with '-config CONFIG_FILE' or '-CONFIG' "
              + "(for example, '-default', '-predicateAnalysis', or '-valueAnalysis'). "
              + "See README.md for more details.");
    }

    // finally call createInstance
    ConfigurableProgramAnalysis cpa;
    try {
      cpa = factory.createInstance();
    } catch (IllegalStateException e) {
      throw new InvalidComponentException(cpaClass, "CPA", e);
    }
    if (cpa == null) {
      throw new InvalidComponentException(cpaClass, "CPA", "Factory returned null.");
    }
    logger.log(Level.FINER, "Sucessfully instantiated CPA " + cpa.getClass().getName() + " with alias " + cpaAlias);
    return cpa;
  }

  private String getCPAAlias(
      String optionValue, String optionName, List<String> optionParts, String pCpaName)
      throws InvalidConfigurationException {

    if (optionParts.size() == 1) {
      // no user-specified alias, use last part of class name
      int dotIndex = pCpaName.lastIndexOf('.');
      return (dotIndex >= 0 ? pCpaName.substring(dotIndex + 1) : pCpaName);

    } else if (optionParts.size() == 2) {
      return optionParts.get(1);

    } else {
      throw new InvalidConfigurationException("Option " + optionName + " contains invalid CPA specification \"" + optionValue + "\"!");
    }
  }

  private Class<?> getCPAClass(String optionName, String pCpaName)
      throws InvalidConfigurationException {
    Class<?> cpaClass;
    try {
      cpaClass = Classes.forName(pCpaName, CPA_CLASS_PREFIX);
    } catch (ClassNotFoundException e) {
      throw new InvalidConfigurationException(
          "Option " + optionName + " is set to unknown CPA " + pCpaName, e);
    }

    if (!ConfigurableProgramAnalysis.class.isAssignableFrom(cpaClass)) {
      throw new InvalidConfigurationException(
        "Option " + optionName + " has to be set to a class implementing the ConfigurableProgramAnalysis interface!");
    }

    Classes.produceClassLoadingWarning(logger, cpaClass, ConfigurableProgramAnalysis.class);

    return cpaClass;
  }

  private CPAFactory getFactoryInstance(String pCpaName, Class<?> cpaClass) throws CPAException {

    // get factory method
    Method factoryMethod;
    try {
      factoryMethod = cpaClass.getMethod("factory", (Class<?>[]) null);
    } catch (NoSuchMethodException e) {
      throw new InvalidComponentException(cpaClass, "CPA", "No public static method \"factory\" with zero parameters.");
    }

    // verify signature
    if (!Modifier.isStatic(factoryMethod.getModifiers())) {
      throw new InvalidComponentException(cpaClass, "CPA", "Factory method is not static.");
    }

    String exception = Classes.verifyDeclaredExceptions(factoryMethod, CPAException.class);
    if (exception != null) {
      throw new InvalidComponentException(cpaClass, "CPA", "Factory method declares the unsupported checked exception " + exception + " .");
    }

    // invoke factory method
    Object factoryObj;
    try {
      factoryObj = factoryMethod.invoke(null, (Object[])null);

    } catch (IllegalAccessException e) {
      throw new InvalidComponentException(cpaClass, "CPA", "Factory method is not public.");

    } catch (InvocationTargetException e) {
      Throwable cause = e.getCause();
      Throwables.propagateIfPossible(cause, CPAException.class);

      throw new UnexpectedCheckedException("instantiation of CPA " + pCpaName, cause);
    }

    if ((factoryObj == null) || !(factoryObj instanceof CPAFactory)) {
      throw new InvalidComponentException(cpaClass, "CPA", "Factory method did not return a CPAFactory instance.");
    }

    return (CPAFactory)factoryObj;
  }

  private boolean createAndSetChildrenCPAs(
      String pCpaName,
      String cpaAlias,
      CPAFactory factory,
      Set<String> usedAliases,
      List<ConfigurableProgramAnalysis> cpas,
      final CFA cfa,
      final Specification specification,
      AggregatedReachedSets pAggregatedReachedSets)
      throws InvalidConfigurationException, CPAException {
    String childOptionName = cpaAlias + ".cpa";
    String childrenOptionName = cpaAlias + ".cpas";

    // Here we need to use these deprecated methods because we dynamically create the key.
    @SuppressWarnings("deprecation")
    String childCpaName = config.getProperty(childOptionName);
    @SuppressWarnings("deprecation")
    String childrenCpaNames = config.getProperty(childrenOptionName);

    if (childrenCpaNames == null && childCpaName == null && cpaAlias.equals("CompositeCPA")
        && cpas != null && !cpas.isEmpty()) {
      // if a specification was given, but no CPAs, insert a LocationCPA
      childrenCpaNames = LocationCPA.class.getCanonicalName();
    }

    if (childCpaName != null) {
      // only one child CPA
      if (childrenCpaNames != null) {
        throw new InvalidConfigurationException("Ambiguous configuration: both "
            + childOptionName + " and " + childrenOptionName + " are specified!");
      }

      ConfigurableProgramAnalysis child =
          buildCPAs(
              childCpaName,
              childOptionName,
              usedAliases,
              cpas,
              cfa,
              specification,
              pAggregatedReachedSets);
      try {
        factory.setChild(child);
      } catch (UnsupportedOperationException e) {
        throw new InvalidConfigurationException(
            pCpaName + " is no wrapper CPA, but option " + childOptionName + " was specified!", e);
      }
      logger.log(Level.FINER, "CPA " + cpaAlias + " got child " + childCpaName);
      return true;

    } else if (childrenCpaNames != null) {
      // several children CPAs
      ImmutableList.Builder<ConfigurableProgramAnalysis> childrenCpas = ImmutableList.builder();

      for (String currentChildCpaName : LIST_SPLITTER.split(childrenCpaNames)) {
        childrenCpas.add(
            buildCPAs(
                currentChildCpaName,
                childrenOptionName,
                usedAliases,
                cpas,
                cfa,
                specification,
                pAggregatedReachedSets));
      }
      if (cpas != null) {
        childrenCpas.addAll(cpas);
        cpas.clear();
      }

      try {
        factory.setChildren(childrenCpas.build());
      } catch (UnsupportedOperationException e) {
        throw new InvalidConfigurationException(
            pCpaName + " is no wrapper CPA, but option " + childrenOptionName + " was specified!",
            e);
      }
      logger.log(Level.FINER, "CPA " + cpaAlias + " got children " + childrenCpaNames);
      return true;
    }
    return false;
  }
}
