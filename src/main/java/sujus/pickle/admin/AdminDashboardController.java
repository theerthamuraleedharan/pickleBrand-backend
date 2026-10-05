package sujus.pickle.admin;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/dashboard")
public class AdminDashboardController {

    private final AdminDashboardService dashboardService;

    public AdminDashboardController(AdminDashboardService dashboardService) {
        this.dashboardService = dashboardService;
    }

    // Returns the aggregate counts used on the admin summary dashboard.
    @GetMapping("/summary")
    public AdminDashboardSummaryResponse getSummary() {
        return dashboardService.getSummary();
    }
}