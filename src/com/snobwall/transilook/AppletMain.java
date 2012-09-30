package com.snobwall.transilook;

import javax.swing.JApplet;
import javax.swing.SwingUtilities;

import com.snobwall.transilook.ui.ViewPanel;

public class AppletMain extends JApplet {
    
    @Override
    public void init() {
        super.init();
        
        SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
                getContentPane().add(new ViewPanel());
            }
        });
    }
}
