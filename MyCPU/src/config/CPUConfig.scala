package MyCPU.config

object CPUConfig {
    val FETCH_WIDTH = 4;

    val DATA_WIDTH = 32;
    val ADDR_WIDTH = 32;

    val AREG_WIDTH = 5;
    val PREG_WIDTH = AREG_WIDTH + 2;
    val ROBID_WIDTH = AREG_WIDTH + 2;
    
    
}