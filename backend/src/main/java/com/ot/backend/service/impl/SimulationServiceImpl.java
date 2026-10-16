package com.ot.backend.service.impl;

import com.ot.backend.service.SimulationService;
import org.springframework.stereotype.Service;

@Service
public class SimulationServiceImpl implements SimulationService {

    @Override
    public void startSimulation() {
        System.out.println("Simulation Started");
    }

    @Override
    public void stopSimulation() {
        System.out.println("Simulation Stopped");
    }

}