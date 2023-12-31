/* *********************************************************************** *
 * project: org.matsim.*
 *                                                                         *
 * *********************************************************************** *
 *                                                                         *
 * copyright       : (C) 2019 by the members listed in the COPYING,        *
 *                   LICENSE and WARRANTY file.                            *
 * email           : info at matsim dot org                                *
 *                                                                         *
 * *********************************************************************** *
 *                                                                         *
 *   This program is free software; you can redistribute it and/or modify  *
 *   it under the terms of the GNU General Public License as published by  *
 *   the Free Software Foundation; either version 2 of the License, or     *
 *   (at your option) any later version.                                   *
 *   See also COPYING, LICENSE and WARRANTY file                           *
 *                                                                         *
 * *********************************************************************** */

package org.matsim.optDRT;

import org.matsim.api.core.v01.Scenario;
import org.matsim.contrib.drt.fare.DrtFareParams;
import org.matsim.contrib.dvrp.fleet.FleetSpecification;
import org.matsim.contrib.dvrp.run.AbstractDvrpModeModule;
import org.matsim.core.api.experimental.events.EventsManager;
import org.matsim.core.config.Config;
import org.matsim.core.router.MainModeIdentifier;
import org.matsim.optDRT.OptDrtConfigGroup.FleetSizeAdjustmentApproach;
import org.matsim.optDRT.OptDrtConfigGroup.ServiceAreaAdjustmentApproach;

import static org.matsim.optDRT.OptDrtConfigGroup.FareAdjustmentApproach.*;
import static org.matsim.optDRT.OptDrtConfigGroup.FleetSizeAdjustmentApproach.ProfitThreshold;
import static org.matsim.optDRT.OptDrtConfigGroup.FleetSizeAdjustmentApproach.WaitingTimeThreshold;
import static org.matsim.optDRT.OptDrtConfigGroup.ServiceAreaAdjustmentApproach.DemandThreshold;

/**
 * @author ikaddoura
 */

public class OptDrtModule extends AbstractDvrpModeModule {

	private final OptDrtConfigGroup optDrtConfigGroup;
	private final DrtFareParams drtFareParams;
	private final MultiModeOptDrtConfigGroup multiModeOptDrtCfg;

	public OptDrtModule(MultiModeOptDrtConfigGroup multiModeOptDrtCfg, OptDrtConfigGroup optDrtConfigGroup,
			DrtFareParams drtFareParams) {
		super(optDrtConfigGroup.getMode());
		this.multiModeOptDrtCfg = multiModeOptDrtCfg;
		this.optDrtConfigGroup = optDrtConfigGroup;
		this.drtFareParams = drtFareParams;
	}

	@Override
	public void install() {

		// dynamic fare strategy
		if (optDrtConfigGroup.getFareAdjustmentApproach() == Disabled) {
			bindModal(OptDrtFareStrategy.class).to(OptDrtFareStrategyDisabled.class);
		} else if (optDrtConfigGroup.getFareAdjustmentApproach() == AverageWaitingTimeThreshold) {
			bindModal(OptDrtFareStrategy.class).toProvider(modalProvider(
					getter -> new OptDrtFareStrategyWaitingTime(optDrtConfigGroup, getter.get(EventsManager.class),
							getter.get(Scenario.class)))).asEagerSingleton();
		} else if (optDrtConfigGroup.getFareAdjustmentApproach() == WaitingTimePercentileThreshold) {
			bindModal(OptDrtFareStrategy.class).toProvider(modalProvider(
					getter -> new OptDrtFareStrategyWaitingTimePercentile(optDrtConfigGroup,
							getter.get(EventsManager.class), getter.get(Scenario.class)))).asEagerSingleton();
		} else if (optDrtConfigGroup.getFareAdjustmentApproach() == ModeSplitThreshold) {
			bindModal(OptDrtFareStrategy.class).toProvider(modalProvider(
					getter -> new OptDrtFareStrategyModalSplit(optDrtConfigGroup, getter.get(EventsManager.class),
							getter.get(Scenario.class), getter.get(MainModeIdentifier.class), drtFareParams)))
					.asEagerSingleton();
		} else {
			throw new RuntimeException("Unknown fare adjustment approach. Aborting...");
		}
		addEventHandlerBinding().to(modalKey(OptDrtFareStrategy.class));

		// fleet size strategy
		if (optDrtConfigGroup.getFleetSizeAdjustmentApproach() == FleetSizeAdjustmentApproach.Disabled) {
			bindModal(OptDrtFleetStrategy.class).to(OptDrtFleetStrategyDisabled.class);
		} else if (optDrtConfigGroup.getFleetSizeAdjustmentApproach() == ProfitThreshold) {
			bindModal(OptDrtFleetStrategy.class).toProvider(modalProvider(
					getter -> new OptDrtFleetStrategyProfit(getter.getModal(FleetSpecification.class),
							optDrtConfigGroup, getter.get(Scenario.class)))).asEagerSingleton();
		} else if (optDrtConfigGroup.getFleetSizeAdjustmentApproach()
				== FleetSizeAdjustmentApproach.AverageWaitingTimeThreshold) {
			bindModal(OptDrtFleetStrategy.class).toProvider(modalProvider(
					getter -> new OptDrtFleetStrategyAvgWaitingTime(getter.getModal(FleetSpecification.class),
							optDrtConfigGroup, getter.get(Scenario.class)))).asEagerSingleton();
		} else if (optDrtConfigGroup.getFleetSizeAdjustmentApproach() == WaitingTimeThreshold) {
			bindModal(OptDrtFleetStrategy.class).toProvider(modalProvider(
					getter -> new OptDrtFleetStrategyWaitingTimePercentile(getter.getModal(FleetSpecification.class),
							optDrtConfigGroup, getter.get(Config.class)))).asEagerSingleton();
		} else {
			throw new RuntimeException("Unknown fleet size adjustment approach. Aborting...");
		}
		addEventHandlerBinding().to(modalKey(OptDrtFleetStrategy.class));

		// service area strategy
		if (optDrtConfigGroup.getServiceAreaAdjustmentApproach() == ServiceAreaAdjustmentApproach.Disabled) {
			bindModal(OptDrtServiceAreaStrategy.class).to(OptDrtServiceAreaStrategyDisabled.class);
		} else if (optDrtConfigGroup.getServiceAreaAdjustmentApproach() == DemandThreshold) {
			bindModal(OptDrtServiceAreaStrategyDemand.class).toProvider(modalProvider(
					getter -> (new OptDrtServiceAreaStrategyDemand(optDrtConfigGroup, getter.get(Scenario.class)))))
					.asEagerSingleton();
			bindModal(OptDrtServiceAreaStrategy.class).to(modalKey(OptDrtServiceAreaStrategyDemand.class));
		} else {
			throw new RuntimeException("Unknown service area adjustment approach. Aborting...");
		}
		this.addEventHandlerBinding().to(modalKey(OptDrtServiceAreaStrategy.class));

		// optDrtControlerListener
		bindModal(OptDrtControlerListener.class).toProvider(modalProvider(
				getter -> new OptDrtControlerListener(this.multiModeOptDrtCfg, this.optDrtConfigGroup, getter.getModal(OptDrtFareStrategy.class),
						getter.getModal(OptDrtFleetStrategy.class), getter.getModal(OptDrtServiceAreaStrategy.class),
						getter.get(Scenario.class)))).asEagerSingleton();
		addControlerListenerBinding().to(modalKey(OptDrtControlerListener.class));

	}

}

