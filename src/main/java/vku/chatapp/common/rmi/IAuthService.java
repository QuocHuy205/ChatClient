package vku.chatapp.common.rmi;

import vku.chatapp.common.dto.*;

import java.rmi.Remote;
import java.rmi.RemoteException;

public interface IAuthService extends Remote {
    AuthResponse login(LoginRequest request) throws RemoteException;
    AuthResponse register(RegisterRequest request) throws RemoteException;
    boolean logout(String sessionToken) throws RemoteException;
    boolean verifyEmail(String email, String otp) throws RemoteException;
    boolean sendPasswordResetOtp(String email) throws RemoteException;
    boolean resetPassword(String email, String otp, String newPassword) throws RemoteException;
    boolean validateSession(String sessionToken) throws RemoteException;
}