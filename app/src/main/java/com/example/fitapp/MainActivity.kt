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
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.launch
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET

data class Treino(val id: String, val nome: String, val subttitulo: String)
data class Exercicio(val nome: String, val musculo: String)

data class HistoricoCarga(
    val data: String, 
    val peso: String, 
    val reps: String, 
    val exercicio: String, 
    val musculo: String, 
    val treino: String
)

interface ApiService {
    @GET("api/v1/catalogo-exercicios")
    suspend fun getExercicios(): List<Exercicio>
}

object RetrofitClient {
    val instance: ApiService by lazy {
        Retrofit.Builder()
            .baseUrl("https://servidor-fitapp-backend.com/")
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(ApiService::class.java)
    }
}

class FitViewModel : ViewModel() {
    private val db = FirebaseFirestore.getInstance()

    val treinos = mutableStateListOf(
        Treino("1", "Treino A", "Superior - Peito e Costas"),
        Treino("2", "Treino B", "Inferior - Pernas e Glúteos"),
        Treino("3", "Treino C", "Cardio e Abdominais")
    )

    private val mapaExercicios = mapOf(
        "Treino A" to listOf(
            Exercicio("Supino Reto", "Peito"),
            Exercicio("Supino Inclinado", "Peito"),
            Exercicio("Remada Curvada", "Costas"),
            Exercicio("Puxada Alta", "Costas")
        ),
        "Treino B" to listOf(
            Exercicio("Agachamento Livre", "Pernas"),
            Exercicio("Leg Press 45°", "Pernas"),
            Exercicio("Cadeira Extensora", "Pernas"),
            Exercicio("Elevação Pélvica", "Glúteos")
        ),
        "Treino C" to listOf(
            Exercicio("Abdominal Supra", "Abdominais"),
            Exercicio("Prancha Isométrica", "Abdominais"),
            Exercicio("Corrida na Esteira", "Cardio")
        )
    )

    val listaHistorico = mutableStateListOf(
        HistoricoCarga("25/04/2026", "55 kg", "8", "Supino Reto", "Peito", "Treino A"),
        HistoricoCarga("22/04/2026", "52.5 kg", "10", "Supino Reto", "Peito", "Treino A"),
        HistoricoCarga("18/04/2026", "50 kg", "12", "Supino Reto", "Peito", "Treino A")
    )

    init {
        sincronizarCatalogoComServidor()
    }

    private fun sincronizarCatalogoComServidor() {
        viewModelScope.launch {
            try {
                val catalogoOnline = RetrofitClient.instance.getExercicios()
            } catch (e: Exception) {
                println("Retrofit: Falha ao conectar na API. Usando catálogo offline. Erro: ${e.message}")
            }
        }
    }

    fun obterExerciciosDoTreino(nomeTreino: String): List<Exercicio> {
        return mapaExercicios[nomeTreino] ?: listOf(Exercicio("Exercício Livre", "Geral"))
    }

    fun adicionarTreino(nome: String, subtitulo: String) {
        if (nome.isNotBlank()) {
            val novoId = System.currentTimeMillis().toString()
            treinos.add(Treino(novoId, nome, subtitulo))
        }
    }

    fun adicionarNovaSerie(peso: String, reps: String, exercicio: Exercicio, nomeTreino: String) {
        if (peso.isNotBlank() && reps.isNotBlank()) {
            val novoItem = HistoricoCarga("Hoje", "$peso kg", reps, exercicio.nome, exercicio.musculo, nomeTreino)
            listaHistorico.add(0, novoItem)
            salvarNoFirestoreNuvem(novoItem)
        }
    }

    private fun salvarNoFirestoreNuvem(item: HistoricoCarga) {
        val dadosTreino = hashMapOf(
            "data" to item.data,
            "peso" to item.peso,
            "reps" to item.reps,
            "exercicio" to item.exercicio,
            "musculo" to item.musculo,
            "treino" to item.treino,
            "timestamp" to System.currentTimeMillis()
        )

        db.collection("historico_treinos")
            .add(dadosTreino)
            .addOnSuccessListener { }
            .addOnFailureListener { }
    }
}

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
                            val nome = backStackEntry.arguments?.getString("nomeTreino") ?: "Treino A"
                            TelaExecucao(navController, viewModel, nome)
                        }
                        composable("evolucao/{nomeExercicio}") { backStackEntry ->
                            val exercicio = backStackEntry.arguments?.getString("nomeExercicio") ?: "Exercício"
                            TelaEvolucao(navController, viewModel, exercicio)
                        }
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
    var mostrarDialog by remember { mutableStateOf(false) }
    var inputNovoTreino by remember { mutableStateOf("") }
    var inputNovoSubtitulo by remember { mutableStateOf("") }

    if (mostrarDialog) {
        AlertDialog(
            onDismissRequest = { mostrarDialog = false },
            containerColor = Color(0xFF1E1E1E),
            title = { Text("Novo Treino", color = Color.White, fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    OutlinedTextField(
                        value = inputNovoTreino,
                        onValueChange = { inputNovoTreino = it },
                        label = { Text("Nome do Treino (ex: Treino D)", color = Color.Gray) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color(0xFF00E5FF),
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        )
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = inputNovoSubtitulo,
                        onValueChange = { inputNovoSubtitulo = it },
                        label = { Text("Foco (ex: Braços Completo)", color = Color.Gray) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color(0xFF00E5FF),
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        )
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.adicionarTreino(inputNovoTreino, inputNovoSubtitulo)
                        mostrarDialog = false
                        inputNovoTreino = ""
                        inputNovoSubtitulo = ""
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00E5FF))
                ) {
                    Text("Salvar", color = Color.Black, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { mostrarDialog = false }) {
                    Text("Cancelar", color = Color.Gray)
                }
            }
        )
    }

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(
                onClick = { mostrarDialog = true },
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
                    unfocusedContainerColor = Color(0xFF1E1E1E),
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White
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
    
    var serieAtual by remember { mutableIntStateOf(1) }
    val totalSeries = 4

    val listaExercicios = remember { viewModel.obterExerciciosDoTreino(nomeTreino) }
    var indiceExercicioAtual by remember { mutableIntStateOf(0) }
    val exercicioAtual = listaExercicios[indiceExercicioAtual]

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
                            Text("${indiceExercicioAtual + 1} de ${listaExercicios.size}", color = Color(0xFF00E5FF), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))

                    Text(exercicioAtual.nome, fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    Text("Grupo Muscular: ${exercicioAtual.musculo}", color = Color.Gray, fontSize = 14.sp)
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    repeat(totalSeries) { index ->
                        Box(
                            modifier = Modifier
                                .size(width = 40.dp, height = 6.dp)
                                .background(
                                    color = if (index < serieAtual) Color(0xFF00E5FF) else Color(0xFF333333),
                                    shape = RoundedCornerShape(3.dp)
                                )
                        )
                    }
                }
                Text("Série $serieAtual de $totalSeries", color = Color.White, fontSize = 14.sp)
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
                        unfocusedContainerColor = Color(0xFF1E1E1E),
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
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
                        unfocusedContainerColor = Color(0xFF1E1E1E),
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    ),
                    shape = RoundedCornerShape(12.dp)
                )
            }

            Spacer(modifier = Modifier.weight(1f))

            Button(
                onClick = {
                    viewModel.adicionarNovaSerie(pesoInput, repsInput, exercicioAtual, nomeTreino)
                    
                    if (serieAtual < totalSeries) {
                        serieAtual++
                    } else {
                        if (indiceExercicioAtual < listaExercicios.lastIndex) {
                            indiceExercicioAtual++
                            serieAtual = 1
                        } else {
                            navController.navigate("evolucao/${exercicioAtual.nome}")
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth().height(50.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00E5FF)),
                shape = RoundedCornerShape(25.dp)
            ) {
                Text(
                    text = if (serieAtual < totalSeries) "Confirmar Série $serieAtual" 
                           else if (indiceExercicioAtual < listaExercicios.lastIndex) "Próximo Exercício" 
                           else "Finalizar Treino", 
                    color = Color.Black, 
                    fontWeight = FontWeight.Bold, 
                    fontSize = 16.sp
                )
            }
        }
    }
}

@Composable
fun TelaEvolucao(navController: NavController, viewModel: FitViewModel, nomeExercicio: String) {
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
                    modifier = Modifier.clickable { navController.navigate("meus_treinos") { popUpTo(0) } },
                    tint = Color.White
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text("Evolução", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Color.White)
            }
            Text("$nomeExercicio - Histórico de cargas", color = Color.Gray, fontSize = 14.sp)
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
            Text("Histórico do Exercício", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = Color.White)
            Spacer(modifier = Modifier.height(12.dp))

            LazyColumn(modifier = Modifier.fillMaxWidth()) {
                val historicoDesteExercicio = viewModel.listaHistorico.filter { it.exercicio == nomeExercicio }
                
                items(historicoDesteExercicio) { item ->
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
                                Text("${item.reps} repetições | Músculo: ${item.musculo}", color = Color.Gray, fontSize = 12.sp)
                                Text("Origem: ${item.treino}", color = Color(0xFF00E5FF).copy(alpha = 0.6f), fontSize = 11.sp)
                            }
                            Text(item.peso, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color(0xFF00E5FF))
                        }
                    }
                }
            }
        }
    }
}