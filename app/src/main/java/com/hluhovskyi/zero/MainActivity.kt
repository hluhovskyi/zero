package com.hluhovskyi.zero

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.Button
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.hluhovskyi.zero.accounts.StubAccountRepository
import com.hluhovskyi.zero.common.AttachWithView
import com.hluhovskyi.zero.common.invoke
import com.hluhovskyi.zero.currencies.StubCurrencyRepository
import com.hluhovskyi.zero.transactions.TransactionComponent
import com.hluhovskyi.zero.transactions.edit.TransactionEditComponent
import com.hluhovskyi.zero.ui.theme.ZeroTheme

class MainActivity : ComponentActivity() {

    private val databaseComponent = DatabaseComponent.factory()
        .create(
            dependencies = object : DatabaseComponent.Dependencies {
                override val context: Context = this@MainActivity
            }
        )
    private val transactionComponent = TransactionComponent.factory()
        .create(
            dependencies = object : TransactionComponent.Dependencies {},
            transactionRepository = databaseComponent.transactionRepository
        )
    private val transactionEditComponent = TransactionEditComponent.factory()
        .create(
            dependencies = object : TransactionEditComponent.Dependencies {},
            transactionRepository = databaseComponent.transactionRepository,
            currencyRepository = StubCurrencyRepository(),
            accountRepository = StubAccountRepository()
        )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            ZeroTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colors.background
                ) {
                    val navController = rememberNavController()
                    NavHost(navController = navController, startDestination = "home") {
                        composable("home") {
                            Column {
                                transactionComponent.viewProvider()
                                Button(
                                    onClick = {
                                        navController.navigate("transactions/edit")
                                    },
                                ) {
                                    Text(text = "Edit transaction")
                                }
                            }
                        }
                        composable("transactions/edit") {
                            transactionEditComponent.AttachWithView()
                        }
                    }
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun DefaultPreview() {

}