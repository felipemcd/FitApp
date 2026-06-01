package com.example.fitapp

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import kotlinx.coroutines.launch
import com.google.firebase.firestore.FirebaseFirestore
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET

// --- CLASSES DE DADOS E RETROFIT ---
data class Treino(val id: String, val nome: String, val subttitulo: String)
data class HistoricoCarga(val data: String, val peso: String)
data class ExercicioDTO(val name: String, val target: String)

interface ApiService {
    @GET("exercicios")
    suspend fun getExercicios(): List<ExercicioDTO>
}

object RetrofitClient {
    val instance: ApiService by lazy {
        Retrofit.Builder()
            .baseUrl("https://exemplo.com/")
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(ApiService::class.java)
    }
}

// --- VIEWMODEL ---
class FitViewModel : ViewModel() {
    private val db = FirebaseFirestore.getInstance()

    val treinos = listOf(
        Treino("1", "Treino A", "Superior - Peito e Costas"),
        Treino("2", "Treino B", "Inferior - Pernas e Glúteos"),
        Treino("3", "Treino C", "Cardio e Abdominais")
    )

    var exerciciosDaApi = mutableStateOf<List<ExercicioDTO>>(emptyList())
    var carregandoApi = mutableStateOf(false)

    val listaHistorico = mutableStateListOf(
        HistoricoCarga("25/04/2026", "55 kg"),
        HistoricoCarga("22/04/2026", "52.5 kg"),
        HistoricoCarga("18/04/2026", "50 kg")
    )

    init {
        buscarExerciciosDaAPI()
    }

    private fun buscarExerciciosDaAPI() {
        viewModelScope.launch {
            carregandoApi.value = true
            try {
                val resultado = RetrofitClient.instance.getExercicios()
                exerciciosDaApi.value = resultado
            } catch (e: Exception) {
                exerciciosDaApi.value = listOf(
                    ExercicioDTO("Supino Reto", "Peito"),
                    ExercicioDTO("Agachamento Livre", "Pernas"),
                    ExercicioDTO("Desenvolvimento", "Ombros")
                )
            } finally {
                carregandoApi.value = false
            }
        }
    }

    fun adicionarNovaSerie(peso: String) {
        if (peso.isNotBlank()) {
            val novoItem = HistoricoCarga("Hoje", "$peso kg")
            listaHistorico.add(0, novoItem)
            salvarNoFirestoreNuvem(novoItem)
        }
    }

    private fun salvarNoFirestoreNuvem(item: HistoricoCarga) {
        val dadosTreino = hashMapOf(
            "data" to item.data,
            "peso" to item.peso,
            "timestamp" to System.currentTimeMillis()
        )

        db.collection("historico_treinos")
            .add(dadosTreino)
            .addOnSuccessListener { /* Sincronizado nas nuvens */ }
            .addOnFailureListener { /* Fallback */ }
    }
}

// --- MAIN ACTIVITY E TELAS ---
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val viewModel = FitViewModel()

        setContent {
            val fitColors = darkColorScheme(
                background = Color(0xFF121212),
                surface = Color(0xFF1E1E1E),
                primary = Color(0xFF00E5FF),
                onBackground = Color.White,
                onSurface = Color.White
            )

            MaterialTheme(colorScheme = fitColors) {
                val navController = rememberNavController()

                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    NavHost(navController = navController, startDestination = "meus_treinos") {
                        composable("meus_treinos") { TelaMeusTreinos(navController, viewModel) }
                        composable("execucao/{nomeTreino}") { backStackEntry ->
                            val nome = backStackEntry.arguments?.getString("nomeTreino") ?: "Treino"
                            TelaExecucao(navController, viewModel, nome)
                        }
                        composable("evolucao") { TelaEvolucao(navController, viewModel) }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TelaMeusTreinos(navController: NavController, viewModel: FitViewModel) {
    var textoBusca by remember { mutableStateOf("") }

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(
                onClick = { },
                containerColor = Color(0xFF00E5FF),
                contentColor = Color.Black,
                shape = RoundedCornerShape(16.dp)
            ) {
                Text("+", fontSize = 24.sp, fontWeight = FontWeight.Bold)
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .padding(paddingValues)
                .fillMaxSize()
                .background(Color(0xFF121212))
                .padding(24.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("+fit", fontSize = 28.sp, fontWeight = FontWeight.Black, color = Color(0xFF00E5FF))
                Box(
                    modifier = Modifier
                        .background(Color(0xFF1E1E1E), RoundedCornerShape(12.dp))
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text("Meus Treinos", color = Color(0xFF00E5FF), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
            Text("Protótipo de Interface Mobile", color = Color.Gray, fontSize = 12.sp)
            Spacer(modifier = Modifier.height(24.dp))

            Text("Meus Treinos", fontSize = 26.sp, fontWeight = FontWeight.Bold, color = Color.White)
            Text("Gerencie suas rotinas de exercícios", color = Color.Gray, fontSize = 14.sp)
            Spacer(modifier = Modifier.height(16.dp))

            OutlinedTextField(
                value = textoBusca,
                onValueChange = { textoBusca = it },
                placeholder = { Text("Buscar treino...", color = Color.Gray) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = Color.Gray) },
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Color(0xFF00E5FF),
                    unfocusedBorderColor = Color(0xFF333333),
                    focusedContainerColor = Color(0xFF1E1E1E),
                    unfocusedContainerColor = Color(0xFF1E1E1E)
                ),
                shape = RoundedCornerShape(14.dp)
            )
            Spacer(modifier = Modifier.height(16.dp))

            LazyColumn {
                val listaFiltrada = viewModel.treinos.filter {
                    it.nome.contains(textoBusca, ignoreCase = true) || it.subttitulo.contains(textoBusca, ignoreCase = true)
                }
                items(listaFiltrada) { treino ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp)
                            .clickable { navController.navigate("execucao/${treino.nome}") },
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E)),
                        border = BorderStroke(1.dp, Color(0xFF333333))
                    ) {
                        Column(modifier = Modifier.padding(20.dp)) {
                            Text(treino.nome, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.White)
                            Text(treino.subttitulo, color = Color.Gray, fontSize = 14.sp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun TelaExecucao(navController: NavController, viewModel: FitViewModel, nomeTreino: String) {
    var pesoInput by remember { mutableStateOf("50") }
    var repsInput by remember { mutableStateOf("12") }

    Scaffold { paddingValues ->
        Column(
            modifier = Modifier
                .padding(paddingValues)
                .fillMaxSize()
                .background(Color(0xFF121212))
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Voltar",
                    modifier = Modifier.clickable { navController.popBackStack() },
                    tint = Color.White
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(nomeTreino, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    Text("Exercício em andamento", color = Color.Gray, fontSize = 12.sp)
                }
            }
            Spacer(modifier = Modifier.height(24.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E)),
                border = BorderStroke(1.dp, Color(0xFF00E5FF).copy(alpha = 0.2f))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("EXERCÍCIO ATUAL", color = Color(0xFF00E5FF), fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        Box(
                            modifier = Modifier
                                .background(Color(0xFF00E5FF).copy(alpha = 0.1f), RoundedCornerShape(12.dp))
                                .padding(horizontal = 12.dp, vertical = 4.dp)
                        ) {
                            Text("3 de 8", color = Color(0xFF00E5FF), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))

                    val exercicioNome = if (viewModel.exerciciosDaApi.value.isNotEmpty()) {
                        viewModel.exerciciosDaApi.value.first().name
                    } else {
                        "Supino Reto"
                    }

                    Text(exercicioNome, fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    Text("Barra com anilhas", color = Color.Gray, fontSize = 14.sp)
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    repeat(4) { index ->
                        Box(
                            modifier = Modifier
                                .size(width = 40.dp, height = 6.dp)
                                .background(
                                    color = if (index < 1) Color(0xFF00E5FF) else Color(0xFF333333),
                                    shape = RoundedCornerShape(3.dp)
                                )
                        )
                    }
                }
                Text("Série 1 de 4", color = Color.White, fontSize = 14.sp)
            }

            Spacer(modifier = Modifier.height(24.dp))

            Column(modifier = Modifier.fillMaxWidth()) {
                Text("Carga (kg)", color = Color.Gray, fontSize = 14.sp)
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = pesoInput,
                    onValueChange = { pesoInput = it },
                    modifier = Modifier.fillMaxWidth(),
                    textStyle = TextStyle(color = Color.White, textAlign = TextAlign.Center, fontSize = 18.sp, fontWeight = FontWeight.Bold),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color(0xFF00E5FF),
                        unfocusedBorderColor = Color(0xFF333333),
                        focusedContainerColor = Color(0xFF1E1E1E),
                        unfocusedContainerColor = Color(0xFF1E1E1E)
                    ),
                    shape = RoundedCornerShape(12.dp)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Column(modifier = Modifier.fillMaxWidth()) {
                Text("Repetições", color = Color.Gray, fontSize = 14.sp)
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = repsInput,
                    onValueChange = { repsInput = it },
                    modifier = Modifier.fillMaxWidth(),
                    textStyle = TextStyle(color = Color.White, textAlign = TextAlign.Center, fontSize = 18.sp, fontWeight = FontWeight.Bold),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color(0xFF00E5FF),
                        unfocusedBorderColor = Color(0xFF333333),
                        focusedContainerColor = Color(0xFF1E1E1E),
                        unfocusedContainerColor = Color(0xFF1E1E1E)
                    ),
                    shape = RoundedCornerShape(12.dp)
                )
            }

            Spacer(modifier = Modifier.weight(1f))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = {}) { Icon(Icons.Default.Refresh, null, tint = Color.Gray) }
                Spacer(modifier = Modifier.width(16.dp))
                IconButton(
                    onClick = {},
                    modifier = Modifier
                        .size(48.dp)
                        .background(Color(0xFF1E1E1E), RoundedCornerShape(24.dp))
                        .border(1.dp, Color(0xFF333333), RoundedCornerShape(24.dp))
                ) { Icon(Icons.Default.PlayArrow, null, tint = Color.White) }
                Spacer(modifier = Modifier.width(16.dp))
                IconButton(onClick = {}) { Icon(Icons.Default.Menu, null, tint = Color.Gray) }
            }

            Spacer(modifier = Modifier.height(24.dp))

            Button(
                onClick = {
                    viewModel.adicionarNovaSerie(pesoInput)
                    navController.navigate("evolucao")
                },
                modifier = Modifier.fillMaxWidth().height(50.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00E5FF)),
                shape = RoundedCornerShape(25.dp)
            ) {
                Text("Confirmar Série", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            }
        }
    }
}

@Composable
fun TelaEvolucao(navController: NavController, viewModel: FitViewModel) {
    Scaffold { paddingValues ->
        Column(
            modifier = Modifier
                .padding(paddingValues)
                .fillMaxSize()
                .background(Color(0xFF121212))
                .padding(24.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Voltar",
                    modifier = Modifier.clickable { navController.navigate("meus_treinos") },
                    tint = Color.White
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text("Evolução", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Color.White)
            }
            Text("Supino Reto - Histórico de cargas", color = Color.Gray, fontSize = 14.sp)
            Spacer(modifier = Modifier.height(24.dp))

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Card(
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E)),
                    border = BorderStroke(1.dp, Color(0xFF333333))
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Recorde", color = Color(0xFF00E5FF), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("55 kg", fontSize = 26.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    }
                }
                Card(
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E)),
                    border = BorderStroke(1.dp, Color(0xFF333333))
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Progresso mensal", color = Color.Gray, fontSize = 12.sp)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("+15%", fontSize = 26.sp, fontWeight = FontWeight.Bold, color = Color(0xFF00E5FF))
                    }
                }
            }

            Spacer(modifier = Modifier.height(28.dp))
            Text("Histórico de Treinos", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = Color.White)
            Spacer(modifier = Modifier.height(12.dp))

            LazyColumn(modifier = Modifier.fillMaxWidth()) {
                items(viewModel.listaHistorico) { item ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 6.dp),
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E)),
                        border = BorderStroke(1.dp, Color(0xFF252525))
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp).fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(item.data, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                Text("Carga máxima", color = Color.Gray, fontSize = 12.sp)
                            }
                            Text(item.peso, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color(0xFF00E5FF))
                        }
                    }
                }
            }
        }
    }
}