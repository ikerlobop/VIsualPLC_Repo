import org.apache.plc4x.java.PlcDriverManager;
import org.apache.plc4x.java.api.PlcConnection;
import org.apache.plc4x.java.api.exceptions.PlcConnectionException;
import org.apache.plc4x.java.api.messages.PlcReadRequest;
import org.apache.plc4x.java.api.messages.PlcReadResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.concurrent.ExecutionException;


public class Visual {

    private static final Logger logger = LoggerFactory.getLogger(Visual.class);
    private static final String CONNECTION_STRING = "s7://192.168.2.12:102";

    public static void main(String[] args) {

        String version = PlcDriverManager.class.getPackage().getImplementationVersion();
        System.out.println("Versión de PLC4X: " + (version != null ? version : "No disponible"));

        System.out.println("\n-----------------------------------------");
        System.out.println("Intentando conectar usando: " + CONNECTION_STRING);


        try (PlcConnection connection = new PlcDriverManager().getConnection(CONNECTION_STRING)) {
            System.out.println("Conexión creada, intentando establecer comunicación...");

            if (!connection.isConnected()) {
                connection.connect();
            }
            
            if (connection.isConnected()) {
                System.out.println("✅ ÉXITO: Conexión establecida correctamente");
                System.out.println("Detalles: " + connection.getMetadata().toString());
                
                try {
            
                    // Acceso a datos  %DB{dbNum}.{offset}:{tipo}
                    PlcReadRequest.Builder builder = connection.readRequestBuilder();
                    builder.addItem("db1", "%DB1.DBW2:INT"); 
                    PlcReadRequest readRequest = builder.build();
                    PlcReadResponse response = readRequest.execute().get();
                    System.out.println("Valor de DB1: " + response.getAllIntegers("db1"));
                    System.out.println("\n✅ RESULTADO: Se logró establecer conexión y leer datos del PLC");

                  
                    

                    
                } catch (InterruptedException | ExecutionException e) {
                    System.out.println("❌ ERROR: No se pudieron leer datos: " + e.getMessage());
                    logger.error("Error al leer datos: {}", e.getMessage());
                    System.out.println("\n✅ RESULTADO: Se logró establecer conexión con el PLC pero falló la lectura de datos");
                }
                
            } else {
                System.out.println("❌ ERROR: No se pudo establecer la conexión");
                System.out.println("\n❌ RESULTADO: Fallo al conectar con el PLC");
            }
            
        } catch (PlcConnectionException e) {
            
        	// errores de conexión
            System.out.println("❌ ERROR: Error de conexión: " + e.getMessage());
            logger.error("Error de conexión con formato {}: {}", CONNECTION_STRING, e.getMessage());
            mostrarSugerenciasDeSolucion();
            
        } catch (Exception e) {
           
        	// Capturamos cualquier otra excepción inesperada
            System.out.println("❌ ERROR: Excepción inesperada: " + e.getMessage());
            logger.error("Excepción inesperada con formato {}", CONNECTION_STRING, e);
            mostrarSugerenciasDeSolucion();
            
        }
    }

    private static void mostrarSugerenciasDeSolucion() {
        System.out.println("\nPosibles soluciones:");
        System.out.println("1. Verifica que el PLC esté encendido y accesible en la red");
        System.out.println("2. Comprueba que puedes hacer ping a 192.168.2.12");
        System.out.println("3. Asegúrate de que el puerto 102 no esté bloqueado por un firewall");
        System.out.println("4. Verifica la versión de la biblioteca PLC4X en tu pom.xml");
        System.out.println("5. Si la conexión falló después de funcionar, reinicia el PLC o verifica su estado");
    }
}

