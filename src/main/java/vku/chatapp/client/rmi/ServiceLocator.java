package vku.chatapp.client.rmi;

import vku.chatapp.common.constants.AppConstants;

public class ServiceLocator {
    private static ServiceLocator instance;
    private RMIClient rmiClient;

    private ServiceLocator() {
        rmiClient = RMIClient.getInstance();
    }

    public static ServiceLocator getInstance() {
        if (instance == null) {
            synchronized (ServiceLocator.class) {
                if (instance == null) {
                    instance = new ServiceLocator();
                }
            }
        }
        return instance;
    }

    public void initialize() throws Exception {
        rmiClient.connect(AppConstants.RMI_HOST, AppConstants.RMI_PORT);
    }

    public RMIClient getRMIClient() {
        return rmiClient;
    }
}