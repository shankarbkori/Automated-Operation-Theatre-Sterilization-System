package com.ot.backend.service.impl;

import com.ot.backend.service.DashboardService;
import org.springframework.stereotype.Service;

@Service
public class DashboardServiceImpl implements DashboardService {

    @Override
    public String getDashboardStatus() {
        return "Dashboard Running";
    }

}