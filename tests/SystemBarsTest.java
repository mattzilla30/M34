package com.m34.viewer;

import android.view.View;
import android.view.WindowInsetsController;

public final class SystemBarsTest {
    static int checks;
    static void check(boolean condition,String message){checks++;if(!condition)throw new AssertionError(message);}
    static final class Controller implements WindowInsetsController {
        int calls,appearance,mask;
        public void setSystemBarsAppearance(int appearance,int mask){calls++;this.appearance=appearance;this.mask=mask;}
    }
    public static void main(String[] args){
        View firstLaunch=new View();Controller dark=new Controller();
        boolean reproduced=false;
        try{firstLaunch.getWindowInsetsController();}catch(IllegalStateException expected){reproduced=true;}
        check(reproduced,"test environment reproduces access before attach");
        int initialRequests=firstLaunch.requests;
        SystemBars.bind(firstLaunch,false);
        check(firstLaunch.requests==initialRequests,"cold launch must not query a missing window controller");
        check(dark.calls==0,"no system bar changes before attach");
        firstLaunch.attach(dark);
        check(dark.calls==1&&dark.appearance==0&&dark.mask==24,"dark appearance after first attach");
        firstLaunch.detach();firstLaunch.attach(dark);
        check(dark.calls==1,"one-shot attach callback removed");
        SystemBars.bind(firstLaunch,true);
        check(dark.calls==2&&dark.appearance==24,"theme change on an attached window");
        View recreated=new View();SystemBars.bind(recreated,true);Controller light=new Controller();recreated.attach(light);
        check(light.calls==1&&light.appearance==24,"activity recreation applies light theme after attach");
        View missingController=new View();SystemBars.bind(missingController,false);missingController.attach(null);
        check(missingController.requests==1,"missing controller is tolerated");
        System.out.println("Passed "+checks+" startup lifecycle checks");
    }
}
