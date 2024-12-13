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

    public static synchronized UserData getInstance(){
        if(userData == null){
            userData = new UserData();
        }
        return userData;
    }

    public void setFirebaseUser(FirebaseUser firebaseUser){
        this.uid = firebaseUser.getUid();
    }

    public String getUid(){
        if(this.uid != null){
            return this.uid;
        }

        return null;
    }
    public void signOut(){
        this.uid = null;
        UserData.userData = null;
    }
}
