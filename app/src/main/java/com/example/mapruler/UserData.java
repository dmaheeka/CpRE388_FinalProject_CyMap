package com.example.mapruler;

import com.google.firebase.auth.FirebaseUser;

/**
 * Singleton class to store FirebaseUser information,
 * Singleton structure allows it to be a global resource across all activities and persist across
 * activity lifecycles.
 */
public class UserData {

    private static UserData userData = null;

    private String uid;

    private UserData(){

    }

    /**
     * Retrieves the stored user data singleton instance
     *
     * @return UserData object
     */
    public static synchronized UserData getInstance(){
        if(userData == null){
            userData = new UserData();
        }
        return userData;
    }

    /**
     * sets the firebase user that is logged in.
     *
     * @param firebaseUser User data retrieved from authentication.
     */
    public void setFirebaseUser(FirebaseUser firebaseUser){
        this.uid = firebaseUser.getUid();
    }

    /**
     * Retrieves the User ID of the logged in user
     *
     * @return User ID
     */
    public String getUid(){
        if(this.uid != null){
            return this.uid;
        }

        return null;
    }

    /**
     * Signs the current user out. removes stored data.
     */
    public void signOut(){
        this.uid = null;
        UserData.userData = null;
    }
}
