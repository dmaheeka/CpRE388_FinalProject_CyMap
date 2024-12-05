package com.example.mapruler;

import com.google.firebase.auth.FirebaseUser;

/**
 * Singleton class to store FirebaseUser information,
 * Singleton structure allows it to be a global resource across all activities and persist across
 * activity lifecycles.
 */
public class UserData {

    private static UserData userData = null;

    private FirebaseUser firebaseUser = null;

    private UserData(){

    }

    public static synchronized UserData getInstance(){
        if(userData == null){
            userData = new UserData();
        }
        return userData;
    }

    public void setFirebaseUser(FirebaseUser firebaseUser){
        this.firebaseUser = firebaseUser;
    }

    public String getUid(){
        if(firebaseUser != null){
            return firebaseUser.getUid();
        }

        return null;
    }
}
