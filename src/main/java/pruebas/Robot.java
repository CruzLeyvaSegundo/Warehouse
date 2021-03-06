/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

package pruebas;

import br.usp.icmc.vicg.gl.jwavefront.JWavefrontObject;
import br.usp.icmc.vicg.gl.matrix.Matrix4;
import br.usp.icmc.vicg.gl.util.Shader;
import java.io.File;
import java.io.IOException;
import static java.lang.Math.PI;
import static java.lang.Math.abs;
import java.util.LinkedList;
import javax.media.opengl.GL3;

/**
 *
 * @author JUNIOR
 */
public class Robot {
    private static  Matrix4 modelMatrix;
    private static String[][] local;
    private static JWavefrontObject rueda;
    private static JWavefrontObject tubo;
    private static JWavefrontObject eje;
    private static JWavefrontObject carcasa;  
    private static JWavefrontObject horquilla;  
    private static JWavefrontObject caja; 
    private static Shader shader; // Gerenciador dos shaders
    private static GL3 gl;
    private static LinkedList<Robot> colaRobots;
    private static LinkedList<OperacionRobot> robotsActivos;
    private static LinkedList<Pair> pedidosPendientes;
    //ESTADOS 
    private final int DEJAR_CAJA=0;
    private final int TRAER_CAJA=1;
    
    private final LinkedList<Movimiento> movimientos;  
    private final Motor motor;
    private final Horquilla horq ;
    private float avance  = 0.2f;
    private float avanceCola  = 0.25f;
    private final int ordenRobot;
    private final Punto posHorquilla;
    private float miRotacion;
   
    // Informacion sobre variable miDireccion (Controla hacia donde apunta la horquilla)
    // miDireccion=-90  (La horquilla apunta a la derecha)
    // miDireccion=90   (La horquilla apunta a la izquierda)
    // miDireccion=-180   (La horquilla apunta hacia abajo)
    // miDireccion=180   (La horquilla apunta hacia arriba)  
    private float miDireccion;
    
    private Punto posicionActual;    
    private Punto posicionInicial;
    private Punto direccion;
    private Punto posObj;//Guarda la posicion en el estante donde se encontro una caja o espacio disponible
    
    // vectores unitarios arriba (-z) abajo(+z) derecha(+x) izquierda(-x)
    private final Punto arriba=new Punto(0,0,-1);
    private final Punto abajo=new Punto(0,0,1);
    private final Punto derecha=new Punto(1,0,0);
    private final Punto izquierda=new Punto(-1,0,0);
    
    private boolean tengoCaja;
    private int nivelEstante;



    public Robot(int n,float x , float z) {
        ordenRobot=n;
        posicionActual=new Punto(x,0.0f,z);
        posicionInicial=new Punto(x,0.0f,z);
        posHorquilla= new Punto(x,0.0f,z);
        direccion= new Punto();
        movimientos=new LinkedList<>();
        motor=new Motor();
        horq=new Horquilla();
        marcar(posicionActual, true);    
        miRotacion = 0.0f;
        miDireccion=0.0f;
        colaRobots.addFirst((Robot)this);
        if (abs(x-14)<=0.001)
        {
            if (!(abs(z-44)<=0.001))
            {
                direccion=abajo;
                miDireccion=180.0f;
            }
            else {
                direccion=derecha;
                miDireccion=-90.0f;
                miRotacion = -90.0f;
            }
        }
        else {
            direccion=arriba;
            miRotacion = -180.0f;
            miDireccion=0.0f;
        }
        System.out.println("Configurado robot: "+n+", en la posicicion ("+posicionActual.x+","+posicionActual.y+","+posicionActual.z+")");  
    }
    static void initRobot(GL3 opengl,Shader sh,Matrix4 model,String[][] lc) throws IOException
    {
        colaRobots=new LinkedList<>();
        robotsActivos=new LinkedList<>();
        pedidosPendientes= new LinkedList<>();
        gl=opengl;
        shader=sh;  
        modelMatrix=model;
        local=lc;
        rueda = new JWavefrontObject(new File("./warehouse/miLlanta.obj"));
        carcasa = new JWavefrontObject(new File("./warehouse/carcasa.obj"));
        eje = new JWavefrontObject(new File("./warehouse/eje.obj"));
        tubo = new JWavefrontObject(new File("./warehouse/tubo.obj"));
        horquilla = new JWavefrontObject(new File("./warehouse/horquilla.obj"));  
        caja = new JWavefrontObject(new File("./warehouse/caja.obj")); 
        
        rueda.init(gl, shader);
        rueda.unitize();
        rueda.dump();
        
        carcasa.init(gl, shader);
        carcasa.unitize();
        carcasa.dump();
        
        eje.init(gl, shader);
        eje.unitize();
        eje.dump();
        
        tubo.init(gl, shader);
        tubo.unitize();
        tubo.dump();    
        
        horquilla.init(gl, shader);
        horquilla.unitize();
        horquilla.dump();    
        
        caja.init(gl, shader);
        caja.unitize();
        caja.dump();  
        //System.out.println(" robot cargado");
    } 
    final void marcar (Punto p, boolean s)
    {
        char[] c=local[0][(int)p.z].toCharArray();
        if (s)
        {
            c[(int)p.x]='x';
        }
        else
        {
            c[(int)p.x]='x';
        }
            local[0][(int)p.z]=new String(c);
    }    
    //ESTADOS DE CAJAS EN LOS ESTANTES
        //Vacio =  "No hay caja en esa prosicion(Espacio libre para ocupara caja)"
        // 0 = "Hay una caja disponible para retirar en esa posicion"
        // 1 = "Caja ya reservada para ser retirada"
        // 2 = "Espacio reservado para una caja"
    private DescritorEstante buscarEspacioEstante(int estante,int estadoRobot)
    {
        char cBuscado=' ';
        char cBandera='2';
        int rangoH1=4,rangoH2=5,rangoV1=4,rangoV2=8;
        int interH=7,interV=8;
        int c=0;        
        boolean encontrado=false;
        int lado=0;
        Punto posEstadoCaja=null;
        int piso=0;
        if(estadoRobot==1)
        {
            cBuscado='0';
            cBandera='1';
        }
        for(int i=1;i<=3;i++) 
        {
            rangoH1=4;rangoH2=5;
            for(int j=1;j<=4;j++) 
            {
                c++;
                if(estante==c)
                {
                    for(int p=1;p<=2;p++) 
                    {
                        for(int k=rangoH1;k<=rangoH2;k++) 
                        {
                            for(int l=rangoV2;l>=rangoV1;l--) 
                            {
                                if(local[p][l].charAt(k)==cBuscado)
                                {
                                    if(k==rangoH2)
                                        lado=1;
                                    encontrado=true;
                                    posEstadoCaja=new Punto(k,0,l);
                                    piso=p;
                                    char[] fila=local[p][l].toCharArray();
                                    fila[k]=cBandera;
                                    local[p][l]=new String(fila);
                                    System.out.println(local[p][l-1]);
                                    System.out.println(local[p][l]);
                                    System.out.println(local[p][l+1]);
                                    return (new DescritorEstante(encontrado,lado,posEstadoCaja,piso));
                                }
                            }
                        }                        
                    }
                } 
                rangoH1+=interH;
                rangoH2+=interH;
            }  
            rangoV1+=interV;
            rangoV2+=interV;
            
        }
        return (new DescritorEstante(encontrado,lado,posEstadoCaja,piso));
    }
    private void calcularRuta(int estanteObj,int estadoRobot)
    {
        DescritorEstante desEstante=buscarEspacioEstante(estanteObj,estadoRobot);
        if(desEstante.encontrado)
        {            
            Punto penultimaDir;
            Punto ultimaDir=null;
            posObj=desEstante.posicion;
            nivelEstante=desEstante.piso;
            float espaciosRetorno;
            System.out.println("espacio : ("+posObj.x+","+posObj.y+","+posObj.z+")");
            movimientos.addFirst(new Movimiento(4,arriba,false,false));
            if(estanteObj==1||estanteObj==5||estanteObj==9)
            {
                movimientos.addFirst(new Movimiento(9,izquierda,false,false));
                if(desEstante.lado==1)
                {
                    float espacios=26-posObj.z;
                    espaciosRetorno=posObj.z-2;
                    movimientos.addFirst(new Movimiento(espacios,arriba,false,false));
                    penultimaDir=arriba;
                    ultimaDir=izquierda;
                    movimientos.addFirst(new Movimiento(0.8f,ultimaDir,false,false));
                    movimientos.addFirst(new Movimiento(-0.8f,ultimaDir,false,false));    
                    movimientos.addFirst(new Movimiento(espaciosRetorno,penultimaDir,false,false));
                    
                    movimientos.addFirst(new Movimiento(5,izquierda,false,false));   
                    movimientos.addFirst(new Movimiento(28,abajo,false,false));
                }
                else
                {
                    float espacios=posObj.z-2;
                    espaciosRetorno=30-posObj.z;////
                    System.out.println("nEspacios : " +espacios);
                    movimientos.addFirst(new Movimiento(24,arriba,false,false));
                    movimientos.addFirst(new Movimiento(5,izquierda,false,false));
                    movimientos.addFirst(new Movimiento(espacios,abajo,false,false));
                    penultimaDir=abajo;
                    ultimaDir=derecha;
                    movimientos.addFirst(new Movimiento(0.8f,ultimaDir,false,false));
                    movimientos.addFirst(new Movimiento(-0.8f,ultimaDir,false,false));   
                    movimientos.addFirst(new Movimiento(espaciosRetorno,penultimaDir,false,false));                 
                }
            }
            else if(estanteObj==2||estanteObj==6||estanteObj==10)
            {
               if(desEstante.lado==1)
                {
                    float espacios=26-posObj.z;
                    espaciosRetorno=posObj.z-2;
                    movimientos.addFirst(new Movimiento(2,izquierda,false,false));
                    movimientos.addFirst(new Movimiento(espacios,arriba,false,false));
                    penultimaDir=arriba;
                    ultimaDir=izquierda;
                    movimientos.addFirst(new Movimiento(0.8f,ultimaDir,false,false));
                    movimientos.addFirst(new Movimiento(-0.8f,ultimaDir,false,false));    
                    movimientos.addFirst(new Movimiento(espaciosRetorno,penultimaDir,false,false));
                    
                    movimientos.addFirst(new Movimiento(12,izquierda,false,false)); 
                    movimientos.addFirst(new Movimiento(28,abajo,false,false));
                }
                else
                {
                    float espacios=26-posObj.z;
                    espaciosRetorno=posObj.z-2;
                    System.out.println("nEspacios : " +espacios);
                    movimientos.addFirst(new Movimiento(7,izquierda,false,false));
                    movimientos.addFirst(new Movimiento(espacios,arriba,false,false));
                    penultimaDir=arriba;
                    ultimaDir=derecha;
                    movimientos.addFirst(new Movimiento(0.8f,ultimaDir,false,false));
                    movimientos.addFirst(new Movimiento(-0.8f,ultimaDir,false,false));    
                    movimientos.addFirst(new Movimiento(espaciosRetorno,penultimaDir,false,false));
                    
                    movimientos.addFirst(new Movimiento(7,izquierda,false,false)); 
                    movimientos.addFirst(new Movimiento(28,abajo,false,false));
                }
            }
            else if(estanteObj==3||estanteObj==7||estanteObj==11)
            {
               if(desEstante.lado==1)
                {
                    float espacios=26-posObj.z;
                    espaciosRetorno=posObj.z-2;
                    movimientos.addFirst(new Movimiento(5,derecha,false,false));
                    movimientos.addFirst(new Movimiento(espacios,arriba,false,false));
                    penultimaDir=arriba;
                    ultimaDir=izquierda; 
                    movimientos.addFirst(new Movimiento(0.8f,ultimaDir,false,false));
                    movimientos.addFirst(new Movimiento(-0.8f,ultimaDir,false,false));   
                    movimientos.addFirst(new Movimiento(espaciosRetorno,penultimaDir,false,false));
                    
                    movimientos.addFirst(new Movimiento(19,izquierda,false,false)); 
                    movimientos.addFirst(new Movimiento(28,abajo,false,false));
                }
                else
                {
                    float espacios=26-posObj.z;
                    espaciosRetorno=posObj.z-2;
                    System.out.println("nEspacios : " +espacios);
                    movimientos.addFirst(new Movimiento(espacios,arriba,false,false));
                    penultimaDir=arriba;
                    ultimaDir=derecha;
                    movimientos.addFirst(new Movimiento(0.8f,ultimaDir,false,false));
                    movimientos.addFirst(new Movimiento(-0.8f,ultimaDir,false,false));   
                    movimientos.addFirst(new Movimiento(espaciosRetorno,penultimaDir,false,false));
                    
                    movimientos.addFirst(new Movimiento(14,izquierda,false,false)); 
                    movimientos.addFirst(new Movimiento(28,abajo,false,false));
                }
            }
            else if(estanteObj==4||estanteObj==8||estanteObj==12)
            {
               if(desEstante.lado==1)
                {
                    float espacios=26-posObj.z;
                    espaciosRetorno=posObj.z-2;
                    movimientos.addFirst(new Movimiento(12,derecha,false,false));
                    movimientos.addFirst(new Movimiento(espacios,arriba,false,false));
                    penultimaDir=arriba;
                    ultimaDir=izquierda;
                    movimientos.addFirst(new Movimiento(0.8f,ultimaDir,false,false));
                    movimientos.addFirst(new Movimiento(-0.8f,ultimaDir,false,false));
                    movimientos.addFirst(new Movimiento(espaciosRetorno,penultimaDir,false,false));
                    
                    movimientos.addFirst(new Movimiento(26,izquierda,false,false)); 
                    movimientos.addFirst(new Movimiento(28,abajo,false,false));
                }
                else
                {
                    float espacios=26-posObj.z;
                    espaciosRetorno=posObj.z-2;
                    System.out.println("nEspacios : " +espacios);
                    movimientos.addFirst(new Movimiento(7,derecha,false,false));
                    movimientos.addFirst(new Movimiento(espacios,arriba,false,false));
                    penultimaDir=arriba;
                    ultimaDir=derecha;
                    movimientos.addFirst(new Movimiento(0.8f,ultimaDir,false,false));
                    movimientos.addFirst(new Movimiento(-0.8f,ultimaDir,false,false));
                    movimientos.addFirst(new Movimiento(espaciosRetorno,penultimaDir,false,false));
                    
                    movimientos.addFirst(new Movimiento(21,izquierda,false,false)); 
                    movimientos.addFirst(new Movimiento(28,abajo,false,false));
                }
            }       
            if(estadoRobot==DEJAR_CAJA)
            {
                    movimientos.addFirst(new Movimiento(12,derecha,false,false));
                    movimientos.addFirst(new Movimiento(0,abajo,false,true));
            }
            else if(estadoRobot==TRAER_CAJA)
            {
                    movimientos.addFirst(new Movimiento(3,abajo,false,false));
                    movimientos.addFirst(new Movimiento(0.4f,abajo,false,false));
                    movimientos.addFirst(new Movimiento(8,derecha,false,false));
                    movimientos.addFirst(new Movimiento(3.4f,arriba,false,false));
                    movimientos.addFirst(new Movimiento(4,derecha,false,false));
                    movimientos.addFirst(new Movimiento(0,abajo,false,true));             
            }
            
        }
        else
        {
            System.out.println("El estante solicitado esta lleno");
            nivelEstante=0;
        }
    }
    static boolean isColaRobotOrdenada()//Verifica si la cola de robots esta ordenada
    {
        Punto pivote=new Punto(16,0,30);
        int factor=2;
        for (int i = colaRobots.size()-1; i >=0; i--) 
        {
            Robot bot=colaRobots.get(i);            
            Punto posActualBot = bot.getPosicionActual();
            if(!posActualBot.esIgual(pivote.x,0,pivote.z))
                return false;
            if(abs(pivote.x-16)<=0.001&&abs(pivote.z-44)<=0.001)
            {
                pivote.x=14;
                factor=-2;
            }
            else
                pivote.z=pivote.z+factor;
                
        }
        return true;
    }
    
    static Movimiento correctorPosRobot(int posRobot)//Verifica si la cola de robots esta ordenada
    {
        Punto pivote=new Punto(16,0,30);
        int factor=2;
        float desp=0;
        Robot botPivote = colaRobots.get(posRobot);
        Punto ubRobot = botPivote.getPosicionInicial();
        Punto dirRobot= botPivote.getDireccion();
        for (int i = colaRobots.size()-1; i >=0; i--) 
        {
            Robot bot=colaRobots.get(i);            
            Punto posActualBot = bot.getPosicionInicial();
            /*System.out.println("comparando bot("+bot.getOrdenRobot()+") - "+"("+posActualBot.x+","+posActualBot.y+","+posActualBot.z+")");
            System.out.println("           comparado con el pivote ("+pivote.x+",0,"+pivote.z+")");*/
            if(i==posRobot &&!posActualBot.esIgual(pivote.x,0,pivote.z))
            {
                Punto dir=null;
                if(abs(ubRobot.x-14)<=0.001)
                {
                    if(pivote.x==14)
                    {
                        desp=pivote.z-ubRobot.z;
                        dir=bot.getAbajo();
                        bot.setDireccion(dir);
                    }
                    else if(pivote.x==16)
                    {                        
                        if(abs(pivote.z-posActualBot.z)<=0.001)
                        {
                            desp=2;
                            dir=bot.getDerecha();
                            bot.setDireccion(dir);
                        }
                        else
                        {
                            desp=2;
                            //desp=44-ubRobot.z;
                            dir=bot.getAbajo();
                            bot.setDireccion(dir);
                        }
                    }
                }
                else if(abs(ubRobot.x-16)<=0.001)
                {
                    desp=ubRobot.z-pivote.z;
                    dir=bot.getArriba();
                    bot.setDireccion(dir);
                }
                return new Movimiento(desp,dir,false,true);
            }
            if(abs(pivote.x-16)<=0.001&&abs(pivote.z-44)<=0.001)
            {
                pivote.x=14;
                factor=-2;
            }
            else
                pivote.z=pivote.z+factor;
                
        }
        return new Movimiento(0,dirRobot,false,true);
    }
    // Informacion sobre variable miDireccion (Controla hacia donde apunta la horquilla)
        // miDireccion=-90  (La horquilla apunta a la derecha)
        // miDireccion=90   (La horquilla apunta a la izquierda)
        // miDireccion=-180   (La horquilla apunta hacia abajo)
        // miDireccion=0   (La horquilla apunta hacia arriba)
    static void controlarRobots(boolean goRobot,int estado,int estante)//Controla todos los roboys
    {
        if(goRobot)
        {
            if(!colaRobots.isEmpty())
            {
                    if(!isColaRobotOrdenada())         
                    {
                        /*System.out.println("La orden: <estado,estante>= ("+estado+","+estante+")"
                                + " sera archivada...");*/
                        System.out.println("Esperar a que la cola se ordene");
                        //pedidosPendientes.addFirst(new Pair(estado,estante));
                    }
                    else
                    {
                        Robot botActivado=colaRobots.removeLast();
                        //ACA SE CALCULARAN LOS MOVIMIENTOS NECESARIOS PARA TRIPULAR EL BOT
                        botActivado.calcularRuta(estante,estado);
                        if(!botActivado.getMovimientos().isEmpty())
                        {
                            if(estado==0)//0
                            {
                                 botActivado.tengoCaja=true;     
                                 botActivado.descontarCaja();
                            }
                            else if(estado==1)//1
                                botActivado.tengoCaja=false; 
                            robotsActivos.addFirst(new OperacionRobot(botActivado,estado,estante));
                            System.out.println("nMovimientos : "+botActivado.getMovimientos().size());
                        }
                        else
                        {
                            colaRobots.addLast(botActivado);
                        }
                    }
            }
            else
                System.out.println("Todos los robots estan ocupados!! Aguarde un momento porfavor :D");
        }
        /*
        if(!pedidosPendientes.isEmpty()&&!colaRobots.isEmpty()&&isColaRobotOrdenada()) //Se cargan todos los pedidos de operacion
        {
            System.out.println("SUEÑOOOO");
             Pair pedido = pedidosPendientes.removeLast();
             Robot botActivado=colaRobots.removeLast();
             //ACA SE CALCULARAN LOS MOVIMIENTOS NECESARIOS PARA TRIPULAR EL BOT
             if(pedido.estado==0)
                   botActivado.calcularRuta(pedido.estante);
             if(!botActivado.getMovimientos().isEmpty())
             {
                  if(pedido.estado==0)//0
                       botActivado.tengoCaja=true;
                  else if(pedido.estado==1)//1
                        botActivado.tengoCaja=false; 
                   robotsActivos.addFirst(new OperacionRobot(botActivado,pedido.estado,pedido.estante));
                   System.out.println("Pedido cargado");
              }
              else
              {
                    colaRobots.addLast(botActivado);
              }          
        }*/
            
        if(!robotsActivos.isEmpty())//Dibuja los robots activos
        {
            for (int i=robotsActivos.size()-1;i>=0;i--) 
            {
                OperacionRobot opRobot=robotsActivos.get(i);
                Robot bot=opRobot.robot;    
                int estadoRobot=opRobot.estadoRobot;
                //ACA SE MANDAN A EJECUTAR LOS MOVIMIENTOS DEL BOT PREVIAMENTE CALCULADOS
                Movimiento movActual = bot.getMovimientos().getLast();
                if(movActual.direccion.esIgual(0, 0, -1))
                {
                    bot.setMiDireccion(0);
                    bot.setMiRotacion(-180);
                }
                else if(movActual.direccion.esIgual(0, 0, 1))
                {
                    bot.setMiDireccion(180);
                    bot.setMiRotacion(-180);
                }
                else if(movActual.direccion.esIgual(1, 0, 0))
                {
                    bot.setMiDireccion(-90);
                    bot.setMiRotacion(-90);
                }
                else if(movActual.direccion.esIgual(-1, 0, 0))
                {
                    bot.setMiDireccion(90);    
                    bot.setMiRotacion(-90);
                }
                if(abs(movActual.desplazamiento-0.8f)<=0.001||abs(movActual.desplazamiento+0.8f)<=0.001||abs(movActual.desplazamiento-0.4f)<=0.001)
                    bot.setAvance(0.005f);
                else
                    bot.setAvance(0.2f);
                if(!(movActual.desplazamiento==0&&movActual.fin==true))
                    bot.actuar(movActual.desplazamiento,movActual.direccion,estadoRobot,movActual.fin);   
                else
                {
                    Robot nuevoBot=new Robot(bot.getOrdenRobot(),14,30);
                    robotsActivos.remove(i);
                }
            }
        }
        if(!colaRobots.isEmpty())  //Dibuja los robots inactivas e ordena la cola de Robots
        {
            for (int i=colaRobots.size()-1;i>=0;i--) 
            {  
                Robot bot=colaRobots.get(i);   
                //ACA SE MANDAN A EJECUTAR LOS MOVIMIENTOS DEL BOT PREVIAMENTE CALCULADOS
                Movimiento movActual = correctorPosRobot(i);
                if(movActual.direccion.esIgual(0, 0, -1))
                {
                    bot.setMiDireccion(0);
                    bot.setMiRotacion(-180);
                    bot.animarRobotCola(movActual.desplazamiento,movActual.direccion);
                }
                else if(movActual.direccion.esIgual(0, 0, 1))
                {
                    bot.setMiDireccion(180);
                    bot.setMiRotacion(-180);
                    bot.animarRobotCola(movActual.desplazamiento,movActual.direccion); 
                    if(abs(bot.getPosicionInicial().z-44)<=0.001)
                    {
                        bot.setMiDireccion(-90);
                        bot.setMiRotacion(-90);
                        bot.setDireccion(bot.getDerecha());                                
                    }
                }
                else if(movActual.direccion.esIgual(1, 0, 0))
                {
                    bot.setMiDireccion(-90);
                    bot.setMiRotacion(-90);
                    bot.animarRobotCola(movActual.desplazamiento,movActual.direccion); 
                    if(abs(bot.getPosicionInicial().x-16)<=0.001)
                    {
                        bot.setMiDireccion(0);
                        bot.setMiRotacion(-180);
                        bot.setDireccion(bot.getArriba());    
                    }
                        
                }
                /*System.out.println("Datos actual Robot["+bot.getOrdenRobot()+"] :("+
                            bot.getPosicionInicial().x+","+bot.getPosicionInicial().y+","+bot.getPosicionInicial().z+") con direccion: "+"("+
                            bot.getDireccion().x+","+bot.getDireccion().y+","+bot.getDireccion().z+") "); 
                System.out.println("          Movimiento Robot["+bot.getOrdenRobot()+"] : "+movActual.desplazamiento+" con direccion: "+"("+
                            movActual.direccion.x+","+movActual.direccion.y+","+movActual.direccion.z+") "); */
            }
                
        }
    }  
    public void actuar(float pasos,Punto dir,int operacion,boolean isFinal)   
    {
        float pos=0;  
        if(abs(pasos-0.8f)<=0.001||abs(pasos+0.8f)<=0.001)
            if(nivelEstante==1)
                pos=0;
            else if(nivelEstante==2)
                pos=0.8f;
        modelMatrix.push();    
        modelMatrix.translate(posicionActual.x,posicionActual.y,posicionActual.z);
        modelMatrix.scale(0.6f, 0.6f, 0.6f);
        motor.avanzar(miRotacion);
        modelMatrix.pop();
        posHorquilla.inicio(posicionActual.x, posicionActual.y,posicionActual.z);
        modelMatrix.push();
        modelMatrix.translate(posHorquilla.x,posHorquilla.y,posHorquilla.z);      
        horq.avanzar(-360.0f+miDireccion, tengoCaja,pos);
        modelMatrix.pop();    
        Punto ir=new Punto(posicionInicial.x, posicionInicial.y,posicionInicial.z);
        ir.sumar(pasos,dir);  
        //System.out.println("Destino Robot["+ordenRobot+"] :("+ir.x+","+ir.y+","+ir.z+")");  
        if(posicionActual.esIgual(ir.x,ir.y,ir.z))
        {
             posicionActual = ir;    
             posicionInicial.inicio(ir.x, ir.y, ir.z);
             if(!isFinal)
             {
                if(abs(movimientos.getLast().desplazamiento)-0.8f<=0.001&&operacion==DEJAR_CAJA)
                {
                    tengoCaja=false;
                    char[] fila=local[nivelEstante][(int)posObj.z].toCharArray();
                    fila[(int)posObj.x]='0';
                    local[nivelEstante][(int)posObj.z]=new String(fila);  
                }
                else if(abs(movimientos.getLast().desplazamiento)-0.8f<=0.001&&operacion==TRAER_CAJA)
                {
                    tengoCaja=true;
                    char[] fila=local[nivelEstante][(int)posObj.z].toCharArray();
                    fila[(int)posObj.x]=' ';
                    local[nivelEstante][(int)posObj.z]=new String(fila);  
                }
                if(abs(movimientos.getLast().desplazamiento)-0.4f<=0.01&&operacion==TRAER_CAJA)
                {
                    tengoCaja=false;
                    adicionarCaja();
                }
                if(abs(movimientos.getLast().desplazamiento)+0.8f<=0.001)
                {
                    nivelEstante=1;
                }
                movimientos.removeLast();       
                 //System.out.println("siguiente paso: " + movimientos.getLast().desplazamiento);
             }
             /*System.out.println("Pos final actual Robot["+ordenRobot+"] :("+
                            posicionInicial.x+","+posicionInicial.y+","+posicionInicial.z+") con direccion: "+"("+
                            dir.x+","+dir.y+","+dir.z+") "); */
             //System.out.println("Posicicion2 actual Robot["+ordenRobot+"] :("+posicionActual.x+","+posicionActual.y+","+posicionActual.z+")");  
        }
        else
        {
            if(pasos<0)
                avance=-avance;           
            posicionActual.sumar(avance, dir);  
           /*  System.out.println("Pos actual Robot["+ordenRobot+"] :("+
                            posicionActual.x+","+posicionActual.y+","+posicionActual.z+") con direccion: "+"("+
                            dir.x+","+dir.y+","+dir.z+") ");  */          
            //System.out.println("Posicicion1 actual Robot["+ordenRobot+"] :("+posicionActual.x+","+posicionActual.y+","+posicionActual.z+")");             
        }
    }
    public void animarRobotCola(float pasos,Punto dir)   
    {
        float pos=0;  
        modelMatrix.push();    
        modelMatrix.translate(posicionActual.x,posicionActual.y,posicionActual.z);
        modelMatrix.scale(0.6f, 0.6f, 0.6f);
        motor.avanzar(miRotacion);
        modelMatrix.pop();
        posHorquilla.inicio(posicionActual.x, posicionActual.y,posicionActual.z);
        modelMatrix.push();
        modelMatrix.translate(posHorquilla.x,posHorquilla.y,posHorquilla.z);      
        horq.avanzar(-360.0f+miDireccion, tengoCaja,pos);
        modelMatrix.pop();    
        Punto ir=new Punto(posicionInicial.x, posicionInicial.y,posicionInicial.z);
        ir.sumar(pasos,dir);  
        //System.out.println("Destino Robot["+ordenRobot+"] :("+ir.x+","+ir.y+","+ir.z+")");  
        if(posicionActual.esIgual(ir.x,ir.y,ir.z))
        {
             posicionActual = ir;    
             posicionInicial.inicio(ir.x, ir.y, ir.z);
             /*System.out.println("Pos final actual Robot["+ordenRobot+"] :("+
                            posicionInicial.x+","+posicionInicial.y+","+posicionInicial.z+") con direccion: "+"("+
                            dir.x+","+dir.y+","+dir.z+") "); */
             //System.out.println("Posicicion2 actual Robot["+ordenRobot+"] :("+posicionActual.x+","+posicionActual.y+","+posicionActual.z+")");  
        }
        else
        {         
            posicionActual.sumar(avanceCola, dir);  
           /*  System.out.println("Pos actual Robot["+ordenRobot+"] :("+
                            posicionActual.x+","+posicionActual.y+","+posicionActual.z+") con direccion: "+"("+
                            dir.x+","+dir.y+","+dir.z+") ");  */          
            //System.out.println("Posicicion1 actual Robot["+ordenRobot+"] :("+posicionActual.x+","+posicionActual.y+","+posicionActual.z+")");             
        }
    }  
    private void descontarCaja()
    {
        for(int p=2;p>=0;p--) 
        {
            for(int l=33;l<=42;l++) 
            {
                for(int k=21;k<=27;k++) 
                {
                    if(local[p][l].charAt(k)=='1')
                    {
                        char[] fila=local[p][l].toCharArray();
                        fila[k]='|';
                        local[p][l]=new String(fila);
                        /*
                        System.out.println(local[p][l-1]);
                        System.out.println(local[p][l]);
                        System.out.println(local[p][l+1]);*/
                        return;
                    }
                }
            }                        
        }     
    }  
    private void adicionarCaja()
    {
        for(int p=0;p<=2;p++) 
        {
            for(int l=35;l<=43;l++) 
            {
                for(int k=2;k<=9;k++) 
                {
                    if(local[p][l].charAt(k)=='|')
                    {
                        char[] fila=local[p][l].toCharArray();
                        fila[k]='0';
                        local[p][l]=new String(fila);
                        /*
                        System.out.println(local[p][l-1]);
                        System.out.println(local[p][l]);
                        System.out.println(local[p][l+1]);*/
                        return;
                    }
                }
            }                        
        }     
    }      
    public Punto getPosicionActual() {
        return posicionActual;
    }

    public int getOrdenRobot() {
        return ordenRobot;
    }

    public Punto getPosicionInicial() {
        return posicionInicial;
    }

    public float getAvance() {
        return avance;
    }

    public float getAvanceCola() {
        return avanceCola;
    }
//Direccion unitaria
    public Punto getArriba() {
        return arriba;
    }

    public LinkedList<Movimiento> getMovimientos() {
        return movimientos;
    }

    public Punto getAbajo() {
        return abajo;
    }

    public Punto getDerecha() {
        return derecha;
    }

    public Punto getIzquierda() {
        return izquierda;
    }

    public Punto getDireccion() {
        return direccion;
    }

    public void setPosicionInicial(Punto posicionInicial) {
        this.posicionInicial = posicionInicial;
    }

    public void setPosicionActual(Punto posicionActual) {
        this.posicionActual = posicionActual;
    }

    public void setPosObj(Punto posObj) {
        this.posObj = posObj;
    }

    public void setMiRotacion(float miRotacion) {
        this.miRotacion = miRotacion;
    }

    public void setMiDireccion(float miDireccion) {
        this.miDireccion = miDireccion;
    }

    public void setDireccion(Punto direccion) {
        this.direccion = direccion;
    }



    public void setAvance(float avance) {
        this.avance = avance;
    }

    public void setAvanceCola(float avanceCola) {
        this.avanceCola = avanceCola;
    }
    
    static void dispose(){
        rueda.dispose();
        carcasa.dispose();
        eje.dispose();
        tubo.dispose();
        horquilla.dispose();
    }   
    public class Motor
    {
        float distancia = 0.0f;
        float giro = 0.0f;
        float radio = 1.0f;
        float angulo;

        public Motor() {
            angulo = (float) (((avance / radio)*180)/PI + 1.5);
        }
        void avanzar(float alfa)
        {
            modelMatrix.push();
            modelMatrix.bind();
            carcasa.draw();
            modelMatrix.pop();
            
            modelMatrix.push();
            /*if (atras)
                modelMatrix.rotate(-alfa, 0, 1, 0);
            else*/
                modelMatrix.rotate(alfa, 0, 1, 0);    
            modelMatrix.translate(0,0.3f,0.0f);
            modelMatrix.bind();
            eje.draw();
            modelMatrix.pop();
            
            modelMatrix.push();
            modelMatrix.scale(2.0f,2.5f, 2.00f);
            modelMatrix.translate(0,0.85f,0);
            modelMatrix.bind();
            tubo.draw();
            modelMatrix.pop();

            modelMatrix.push();
            modelMatrix.scale(0.4f, 0.4f, 0.4f);
            modelMatrix.translate(-1.3f,-1.3f,-1.3f);
            modelMatrix.rotate(alfa, 0, 1, 0);
            modelMatrix.rotate(-giro, 1, 0, 0);
            modelMatrix.bind();
            rueda.draw();
            modelMatrix.pop();

            modelMatrix.push();
            modelMatrix.scale(0.4f, 0.4f, 0.4f);
            modelMatrix.translate(-1.3f,-1.3f,1.3f);
            modelMatrix.rotate(alfa, 0, 1, 0);
            modelMatrix.rotate(-giro, 1, 0, 0);
            modelMatrix.bind();
            rueda.draw();
            modelMatrix.pop();

            modelMatrix.push();
            modelMatrix.scale(0.4f, 0.4f, 0.4f);
            modelMatrix.translate(1.3f,-1.3f,1.3f);
            modelMatrix.rotate(alfa, 0, 1, 0);
            modelMatrix.rotate(-giro, 1, 0, 0);
            modelMatrix.bind();
            rueda.draw();
            modelMatrix.pop();

            modelMatrix.push();
            modelMatrix.scale(0.4f, 0.4f, 0.4f);
            modelMatrix.translate(1.3f,-1.3f,-1.3f);
            modelMatrix.rotate(alfa, 0, 1, 0);
            modelMatrix.rotate(-giro, 1, 0, 0);
            modelMatrix.bind();
            rueda.draw();
            modelMatrix.pop();
            giro += angulo;
        }
    }
    public class Horquilla
    {
        void avanzar(float alfa, boolean conCaja,float pos)
        {
            modelMatrix.push();
            modelMatrix.rotate(alfa, 0, 1, 0);
            modelMatrix.translate(0,1.0f+pos, -0.7f);
            modelMatrix.bind();
            horquilla.draw();
            if(conCaja)
            {
                modelMatrix.translate(0,0.1f,-0.23f);
                modelMatrix.bind();
                caja.draw();
            }
            modelMatrix.pop();
        }
    }   
}