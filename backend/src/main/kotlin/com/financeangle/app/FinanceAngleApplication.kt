package com.financeangle.app

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class FinanceAngleApplication

fun main(args: Array<String>) {
    runApplication<FinanceAngleApplication>(*args)
}
