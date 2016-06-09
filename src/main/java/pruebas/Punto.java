/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

package pruebas;

import static java.lang.Math.abs;

/**
 *
 * @author JUNIOR
 */
public class Punto {
    float x;
    float y;
    float z;
    Punto()
    {
        
    }
    Punto(float xo,float yo,float zo)
    {
        x=xo;
        y=yo;
        z=zo;
    }
    void inicio(float xo,float yo,float zo)
    {
        x=xo;
        y=yo;
        z=zo;
    }
    boolean esIgual(float p,float q,float r)
    {
        return abs(p-x)<=0.001f&&abs(q-y)<=0.001f&&abs(r-z)<=0.001f;
    }  
    public void sumar(float t, Punto q)
    {
        x += (t * q.x);
        y += (t * q.y);
        z += (t * q.z);
    }
    public void mult (float m, Punto p)
    {
        x = m * p.x;
        y = m * p.y;
        z = m * p.z;
    }
}
