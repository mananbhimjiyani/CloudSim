package org.cloudbus.cloudsim.examples;

import java.io.FileWriter;
import java.io.IOException;
import org.cloudsimplus.allocationpolicies.VmAllocationPolicySimple;
import org.cloudsimplus.brokers.DatacenterBroker;
import org.cloudsimplus.brokers.DatacenterBrokerSimple;
import org.cloudsimplus.cloudlets.Cloudlet;
import org.cloudsimplus.cloudlets.CloudletSimple;
import org.cloudsimplus.core.CloudSimPlus;
import org.cloudsimplus.datacenters.Datacenter;
import org.cloudsimplus.datacenters.DatacenterSimple;
import org.cloudsimplus.hosts.Host;
import org.cloudsimplus.hosts.HostSimple;
import org.cloudsimplus.listeners.EventInfo;
import org.cloudsimplus.listeners.EventListener;
import org.cloudsimplus.power.models.PowerModelHost;
import org.cloudsimplus.power.models.PowerModelHostSimple;
import org.cloudsimplus.resources.Pe;
import org.cloudsimplus.resources.PeSimple;
import org.cloudsimplus.schedulers.cloudlet.CloudletSchedulerTimeShared;
import org.cloudsimplus.schedulers.vm.VmSchedulerTimeShared;
import org.cloudsimplus.utilizationmodels.UtilizationModel;
import org.cloudsimplus.utilizationmodels.UtilizationModelDynamic;
import org.cloudsimplus.utilizationmodels.UtilizationModelFull;
import org.cloudsimplus.vms.Vm;
import org.cloudsimplus.vms.VmSimple;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import org.jfree.chart.ChartFactory;
import org.jfree.chart.ChartUtils;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.annotations.XYLineAnnotation;
import org.jfree.chart.plot.XYPlot;
import org.jfree.chart.renderer.xy.XYLineAndShapeRenderer;
import org.jfree.data.xy.XYSeries;
import org.jfree.data.xy.XYSeriesCollection;

import java.awt.*;
import java.io.File;
import java.io.IOException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;




public class CloudSimPlusExample {

    private static List<Double> powerOverTime = new ArrayList<>();
    private static List<Double> timeStamps = new ArrayList<>();

    private static final Logger logger = LoggerFactory.getLogger(CloudSimPlusExample.class);

    private static final int HOSTS = 5;
    private static final int VMS = 10;
    private static final int CLOUDLETS = 15;
    private static final double SCHEDULING_INTERVAL = 1.0;
    private static double totalEnergy = 0.0;
    private static Datacenter datacenter;

    public static void main(String[] args) {
        System.out.println("Starting Advanced CloudSim Plus Example...");
        logger.info("CloudSim simulation started.");

        // 1. Initialize CloudSim Plus
        CloudSimPlus simulation = new CloudSimPlus();
        simulation.addOnClockTickListener(createClockTickListener());

        // 2. Create Datacenter
        datacenter = createDatacenter(simulation);

        // 3. Create Broker
        DatacenterBroker broker = new DatacenterBrokerSimple(simulation);

        // 4. Create VMs
        List<Vm> vmList = createVms();

        // 5. Create Cloudlets
        List<Cloudlet> cloudletList = createCloudlets();

        // 6. Submit VMs and Cloudlets with load balancing
        broker.submitVmList(vmList);
        assignCloudletsToVms(broker, cloudletList, vmList); // Custom load balancing

        // 7. Simulate host failure for fault tolerance
        simulation.addOnClockTickListener(event -> {
            if (event.getTime() == 5.0) {
                Host failedHost = datacenter.getHostList().get(0);
                System.out.println("\nSimulating failure of Host 0 at time 5.0");
                failedHost.setFailed(true); // Fail Host 0
                // VMs on Host 0 (VM 0 and VM 5) will be migrated automatically by VmAllocationPolicy
            }
        });

        // 8. Start simulation
        simulation.start();

        logger.info("Simulation completed successfully.");

        // 9. Print results
        printResults(broker);
    }

    private static Datacenter createDatacenter(CloudSimPlus simulation) {
        List<Host> hostList = new ArrayList<>();
        for (int i = 0; i < HOSTS; i++) {
            List<Pe> peList = new ArrayList<>();
            int pes = 8; // Increased PEs to handle more VMs
            long mips = 3000; // Consistent high MIPS

            for (int j = 0; j < pes; j++) {
                peList.add(new PeSimple(mips));
            }

            PowerModelHost powerModel = new PowerModelHostSimple(250, 50); // Max 250W, idle 50W
            Host host = new HostSimple(65536, 40000000, 40000000, peList); // Doubled RAM and storage
            host.setVmScheduler(new VmSchedulerTimeShared());
            host.setPowerModel(powerModel);
            hostList.add(host);
        }

        Datacenter dc = new DatacenterSimple(simulation, hostList, new VmAllocationPolicySimple());
        dc.setSchedulingInterval(SCHEDULING_INTERVAL);
        return dc;
    }

    private static List<Vm> createVms() {
        List<Vm> vmList = new ArrayList<>();
        for (int i = 0; i < VMS; i++) {
            int mips = 2000; // Consistent MIPS
            int pes = 4; // Increased PEs
            long ram = 16384; // 16 GB RAM

            Vm vm = new VmSimple(mips, pes);
            vm.setRam(ram).setBw(4000).setSize(40000); // Increased BW and storage
            vm.setCloudletScheduler(new CloudletSchedulerTimeShared());
            vmList.add(vm);
        }
        return vmList;
    }

    private static List<Cloudlet> createCloudlets() {
        List<Cloudlet> cloudletList = new ArrayList<>();
        UtilizationModel utilizationModel = new UtilizationModelDynamic(0.5);

        for (int i = 0; i < CLOUDLETS; i++) {
            long length = 10000 + (i * 1000); // 10k–29k MI
            int pes = (i % 3 == 0) ? 2 : 1;

            Cloudlet cloudlet = new CloudletSimple(length, pes, utilizationModel);
            cloudlet.setSizes(4096);
            cloudlet.setUtilizationModelCpu(new UtilizationModelFull());
            cloudlet.setUtilizationModelRam(new UtilizationModelDynamic(0.3)); // 30% of VM RAM
            cloudlet.setUtilizationModelBw(new UtilizationModelDynamic(0.5));
            cloudletList.add(cloudlet);
        }
        return cloudletList;
    }

    private static void assignCloudletsToVms(DatacenterBroker broker, List<Cloudlet> cloudletList, List<Vm> vmList) {
        // Simple round-robin load balancing
        for (int i = 0; i < cloudletList.size(); i++) {
            Vm vm = vmList.get(i % vmList.size()); // Distribute evenly across VMs
            cloudletList.get(i).setVm(vm);
            broker.submitCloudlet(cloudletList.get(i));
        }
    }

    private static EventListener<EventInfo> createClockTickListener() {
        return eventInfo -> {
            double currentTime = eventInfo.getTime();
            
    
            // Record power usage every 1 second
            if (currentTime > 0) {
                double currentPower = datacenter.getHostList().stream()
                    .mapToDouble(host -> {
                        double utilization = host.getCpuPercentUtilization();
                        return host.getPowerModel().getPower(utilization);
                    })
                    .sum();
    
                totalEnergy += currentPower * SCHEDULING_INTERVAL / 3600.0; // Convert to Wh
                powerOverTime.add(currentPower);
                timeStamps.add(currentTime);
    
                System.out.printf("\nTime: %.0f sec | Power: %.2f W", currentTime, currentPower);
            }
    
            // Simulate host failure only once at time = 5.0
            if (Math.abs(currentTime - 5.0) < 0.0001) {
                Host failedHost = datacenter.getHostList().get(0);
                System.out.println("\nSimulating failure of Host 0 at time 5.0");
                failedHost.setFailed(true); // Fail Host 0
            }
        };
    }

    private static void generatePowerUsageChart() {
        XYSeries series = new XYSeries("Power Usage (W)");
        for (int i = 0; i < powerOverTime.size(); i++) {
            series.add(timeStamps.get(i), powerOverTime.get(i));
        }
    
        XYSeriesCollection dataset = new XYSeriesCollection();
        dataset.addSeries(series);
    
        JFreeChart chart = ChartFactory.createXYLineChart(
            "Power Usage Over Time",
            "Time (s)",
            "Power (W)",
            dataset
        );
    
        // Improve plot appearance
        XYPlot plot = chart.getXYPlot();
        XYLineAndShapeRenderer renderer = new XYLineAndShapeRenderer();
    
        renderer.setSeriesPaint(0, Color.RED);
        renderer.setSeriesStroke(0, new BasicStroke(2.0f));
        renderer.setSeriesShapesVisible(0, true);
        renderer.setSeriesShape(0, new java.awt.geom.Ellipse2D.Double(-2, -2, 4, 4));
    
        plot.setRenderer(renderer);
    
        // Highlight host failure at 5s
        double failureTime = 5.0;
        double yMin = plot.getRangeAxis().getRange().getLowerBound();
        double yMax = plot.getRangeAxis().getRange().getUpperBound();
        XYLineAnnotation failureLine = new XYLineAnnotation(
            failureTime, yMin, failureTime, yMax,
            new BasicStroke(1.5f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_BEVEL, 1.0f, new float[]{6.0f}, 0.0f),
            Color.BLUE
        );
        plot.addAnnotation(failureLine);
    
        // Anti-aliasing
        chart.setAntiAlias(true);
        chart.getTitle().setFont(new Font("SansSerif", Font.BOLD, 16));
    
        try {
            File imageFile = new File("power_usage_chart.png");
            ChartUtils.saveChartAsPNG(imageFile, chart, 800, 600);
            System.out.println("Chart saved as power_usage_chart.png");
        } catch (IOException e) {
            System.err.println("Error saving chart: " + e.getMessage());
        }
    }
    
    
    

    private static void printResults(DatacenterBroker broker) {
        System.out.println("\n\n========== FINAL RESULTS ==========");
    
        List<Cloudlet> finishedCloudlets = broker.getCloudletFinishedList();
        finishedCloudlets.sort(Comparator.comparingDouble(Cloudlet::getFinishTime));
    
        System.out.println("Cloudlet ID | Status | ExecTime | VM ID | Start Time | Finish Time");
        finishedCloudlets.forEach(c -> System.out.printf("%9d | %6s | %8.2f | %5d | %10.2f | %11.2f%n",
            c.getId(), c.getStatus(), c.getActualCpuTime(), c.getVm().getId(),
            c.getExecStartTime(), c.getFinishTime()));
    
        System.out.printf("\nTotal Energy Consumption: %.2f Wh%n", totalEnergy);
        System.out.printf("Total Simulation Time: %.2f sec%n", finishedCloudlets.isEmpty() ? 0 :
            finishedCloudlets.get(finishedCloudlets.size() - 1).getFinishTime());
    
        // Show number of power samples collected
        System.out.println("Power samples recorded: " + powerOverTime.size());
    
        // Export power usage graph data to CSV
        try (FileWriter writer = new FileWriter("power_usage.csv")) {
            writer.write("Time (s),Power (W)\n");
            for (int i = 0; i < powerOverTime.size(); i++) {
                writer.write(String.format("%.0f,%.2f\n", timeStamps.get(i), powerOverTime.get(i)));
            }
            System.out.println("Power usage data exported to power_usage.csv");
        } catch (IOException e) {
            System.err.println("Error writing power usage data: " + e.getMessage());
        }

        System.out.println("Sample data for plotting:");
for (int i = 0; i < Math.min(10, powerOverTime.size()); i++) {
    System.out.printf("Time: %.2f s, Power: %.2f W%n", timeStamps.get(i), powerOverTime.get(i));
}


        generatePowerUsageChart(); // generate and save PNG chart
        System.out.println("Power usage chart generated.");
    }
}
    