// FILE: vku/chatapp/common/rmi/IPeerDiscoveryService.java
// ✅ ADD: getPeerInfo method

package vku.chatapp.common.rmi;

import vku.chatapp.common.dto.PeerInfo;

import java.rmi.Remote;
import java.rmi.RemoteException;
import java.util.List;

public interface IPeerDiscoveryService extends Remote {

    boolean registerPeer(PeerInfo peerInfo) throws RemoteException;

    boolean unregisterPeer(Long userId) throws RemoteException;

    List<PeerInfo> getOnlineFriends(Long userId) throws RemoteException;

    boolean updateHeartbeat(Long userId) throws RemoteException;

    /**
     * ✅ NEW: Get peer info for a specific user
     * @param userId The user ID to lookup
     * @return PeerInfo if user is online, null otherwise
     */
    PeerInfo getPeerInfo(Long userId) throws RemoteException;
}