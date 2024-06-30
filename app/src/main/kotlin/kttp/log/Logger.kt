package kttp.log

import org.slf4j.LoggerFactory


class Logger(clazz: Class<*>) {


    private val log = LoggerFactory.getLogger(clazz)


    fun trace(format: String, vararg arguments: Any) = log.trace(format, arguments)
    fun info(format: String, vararg arguments: Any) = log.info(format, arguments)
    fun warn(format: String, vararg arguments: Any) = log.warn(format, arguments)
    fun error(format: String, vararg arguments: Any) = log.error(format, arguments)
    fun debug(format: String, vararg arguments: Any) = log.debug(format, arguments)

    fun trace(messageSupplier: () -> String){
        if(log.isTraceEnabled)
            log.trace(messageSupplier())
    }

    fun info(messageSupplier: () -> String){
        if(log.isInfoEnabled)
            log.info(messageSupplier())
    }

    fun warn(messageSupplier: () -> String){
        if(log.isWarnEnabled)
            log.warn(messageSupplier())
    }

    fun error(messageSupplier: () -> String){
        if(log.isErrorEnabled)
            log.error(messageSupplier())
    }

    fun debug(messageSupplier: () -> String){
        if(log.isDebugEnabled)
            log.debug(messageSupplier())
    }



}



