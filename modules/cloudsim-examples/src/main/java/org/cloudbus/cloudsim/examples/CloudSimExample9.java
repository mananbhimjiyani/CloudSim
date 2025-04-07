package org.cloudbus.cloudsim.examples;

import org.cloudbus.cloudsim.*;
import org.cloudbus.cloudsim.core.CloudSim;
import org.cloudbus.cloudsim.provisioners.BwProvisionerSimple;
import org.cloudbus.cloudsim.provisioners.PeProvisionerSimple;
import org.cloudbus.cloudsim.provisioners.RamProvisionerSimple;

import java.text.DecimalFormat;
import java.util.*;

/**
 * A simple example showing the 2 cloudlet scheduling models: time-shared and space-shared.
 */
public class CloudSimExample9 {
    public static DatacenterBroker broker;

    /** The cloudlet list. */
    private static List<Cloudlet> cloudletList;
    /** The vmlist. */
    private static List<Vm> vmlist;

    /**
     * Creates main() to run this example.
     *
     * @param args the args
     */
    public static void main(String[] args) {
        Log.println("Starting CloudSimExample9...");

        try {
            // Initialize CloudSim
            int num_user = 1;
            Calendar calendar = Calendar.getInstance();
            boolean trace_flag = false;

            CloudSim.init(num_user, calendar, trace_flag);

            // Create Datacenter
            createDatacenter("Datacenter_0");

            // Create Broker
            broker = new DatacenterBroker("Broker");
            int brokerId = broker.getId();

            // Create VMs
            vmlist = new ArrayList<>();
            int mips = 1000;
            long size = 10000;
            int ram = 512;
            long bw = 1000;
            int pesNumber = 1;
            String vmm = "Xen";

            // Time-shared VM
            vmlist.add(new Vm(0, brokerId, mips, pesNumber, ram, bw, size, vmm, new CloudletSchedulerTimeShared()));

            // Space-shared VM
            vmlist.add(new Vm(1, brokerId, mips, pesNumber, ram, bw, size, vmm, new CloudletSchedulerSpaceShared()));

            broker.submitGuestList(vmlist);

            // Create Cloudlets
            cloudletList = new ArrayList<>();
            int id = 0;
            long fileSize = 300;
            long outputSize = 300;
            UtilizationModel utilizationModel = new UtilizationModelFull();

            cloudletList.add(createCloudlet(id++, 10000, pesNumber, fileSize, outputSize, utilizationModel, 0));
            cloudletList.add(createCloudlet(id++, 100000, pesNumber, fileSize, outputSize, utilizationModel, 0));
            cloudletList.add(createCloudlet(id++, 1000000, pesNumber, fileSize, outputSize, utilizationModel, 0));
            cloudletList.add(createCloudlet(id++, 10000, pesNumber, fileSize, outputSize, utilizationModel, 1));
            cloudletList.add(createCloudlet(id++, 100000, pesNumber, fileSize, outputSize, utilizationModel, 1));
            cloudletList.add(createCloudlet(id++, 1000000, pesNumber, fileSize, outputSize, utilizationModel, 1));

            broker.submitCloudletList(cloudletList);

            // Start Simulation
            CloudSim.startSimulation();
            CloudSim.stopSimulation();

            // Print Results
            List<Cloudlet> newList = broker.getCloudletReceivedList();
            newList.sort(Comparator.comparing(Cloudlet::getCloudletId));
            printCloudletList(newList);

            Log.println("CloudSimExample9 finished!");
        } catch (Exception e) {
            e.printStackTrace();
            Log.println("Unwanted errors happen");
        }
    }

    /**
     * Creates a cloudlet.
     *
     * @param id               the cloudlet ID
     * @param length           the length of the cloudlet (in MI)
     * @param pesNumber        the number of PEs required
     * @param fileSize         the file size
     * @param outputSize       the output size
     * @param utilizationModel the utilization model
     * @param vmId             the VM ID
     * @return the created cloudlet
     */
    private static Cloudlet createCloudlet(int id, long length, int pesNumber, long fileSize, long outputSize,
                                            UtilizationModel utilizationModel, int vmId) {
        Cloudlet cloudlet = new Cloudlet(id, length, pesNumber, fileSize, outputSize, utilizationModel, utilizationModel, utilizationModel);
        cloudlet.setUserId(broker.getId());
        cloudlet.setGuestId(vmId);
        return cloudlet;
    }

    /**
     * Creates the datacenter.
     *
     * @param name the name of the datacenter
     * @return the created datacenter
     */
    private static Datacenter createDatacenter(String name) {
        List<Host> hostList = new ArrayList<>();
        List<Pe> peList = new ArrayList<>();
        int mips = 1000;

        peList.add(new Pe(0, new PeProvisionerSimple(mips)));

        int ram = 4096; // 4GB RAM
        long storage = 2000000; // 2TB Storage
        int bw = 10000; // Bandwidth

        hostList.add(new Host(0, new RamProvisionerSimple(ram), new BwProvisionerSimple(bw), storage, peList, new VmSchedulerTimeShared(peList)));
        hostList.add(new Host(1, new RamProvisionerSimple(ram), new BwProvisionerSimple(bw), storage, peList, new VmSchedulerTimeShared(peList)));

        String arch = "x86";
        String os = "Linux";
        String vmm = "Xen";
        double time_zone = 10.0;
        double cost = 3.0;
        double costPerMem = 0.05;
        double costPerStorage = 0.001;
        double costPerBw = 0.0;

        DatacenterCharacteristics characteristics = new DatacenterCharacteristics(
                arch, os, vmm, hostList, time_zone, cost, costPerMem, costPerStorage, costPerBw);

        Datacenter datacenter = null;
        try {
            datacenter = new Datacenter(name, characteristics, new VmAllocationPolicySimple(hostList), new LinkedList<>(), 0);
        } catch (Exception e) {
            e.printStackTrace();
        }

        return datacenter;
    }

    /**
     * Prints the Cloudlet objects.
     *
     * @param list the list of Cloudlets
     */
    private static void printCloudletList(List<Cloudlet> list) {
        int size = list.size();
        Cloudlet cloudlet;

        String indent = "    ";
        Log.println();
        Log.println("========== OUTPUT ==========");
        Log.println("Cloudlet ID" + indent + "STATUS" + indent
                + "Data center ID" + indent + "VM ID" + indent + "Time" + indent
                + "Start Time" + indent + "Finish Time");

        DecimalFormat dft = new DecimalFormat("###.##");
        for (Cloudlet value : list) {
            cloudlet = value;
            Log.print(indent + cloudlet.getCloudletId() + indent + indent);

            if (cloudlet.getStatus() == Cloudlet.CloudletStatus.SUCCESS) {
                Log.print("SUCCESS");

                Log.println(indent + indent + cloudlet.getResourceId()
                        + indent + indent + indent + cloudlet.getGuestId()
                        + indent + indent
                        + dft.format(cloudlet.getActualCPUTime()) + indent
                        + indent + dft.format(cloudlet.getExecStartTime())
                        + indent + indent
                        + dft.format(cloudlet.getExecFinishTime()));
            }
        }
    }
}