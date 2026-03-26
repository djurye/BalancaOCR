# ⚖️ Balança OCR — Pesagem Automática via Câmera

Aplicativo Android que usa **câmera em tempo real + OCR (ML Kit)** para ler automaticamente
o display de uma balança digital e registrar cada pesagem de partículas sem intervenção manual.

---

## 🚀 Funcionalidades

| Feature | Descrição |
|---|---|
| 📷 OCR em tempo real | ML Kit lê o display da balança frame a frame |
| ⚖️ Detecção de estabilidade | Registra apenas quando o valor parou de oscilar |
| 📊 Exportação XLSX | Gera planilha Excel com todas as pesagens + estatísticas |
| 🗂️ Sessões | Organize pesagens por lote/sessão |
| ↩️ Desfazer | Remove a última leitura com um toque |
| ⏸️ Pausar/Retomar | Controle manual da captura |
| 🗄️ Histórico local | Banco de dados Room — dados persistem offline |

---

## 📦 Estrutura do Projeto

```
BalancaOCR/
├── app/src/main/
│   ├── java/com/balancaocr/
│   │   ├── camera/
│   │   │   └── ScaleImageAnalyzer.kt   ← CameraX + ML Kit bridge
│   │   ├── data/
│   │   │   ├── Database.kt             ← Room entities, DAOs, DB
│   │   │   └── BalancaRepository.kt    ← Repositório de dados
│   │   ├── ocr/
│   │   │   └── WeightAnalyzer.kt       ← Lógica de estabilidade
│   │   ├── ui/
│   │   │   ├── MainActivity.kt         ← Lista de sessões
│   │   │   ├── CameraActivity.kt       ← Tela principal com câmera
│   │   │   ├── CameraViewModel.kt      ← ViewModel da câmera
│   │   │   └── HistoryActivity.kt      ← Histórico / exportação
│   │   └── utils/
│   │       └── ExcelExporter.kt        ← Apache POI → .xlsx
│   └── res/
│       ├── layout/                     ← Todos os XMLs de tela
│       ├── values/                     ← Colors, strings, themes
│       ├── drawable/                   ← Ícone, guia de enquadramento
│       └── xml/file_paths.xml          ← FileProvider paths
└── build.gradle
```

---

## 🔧 Como compilar (Android Studio)

### Pré-requisitos
- **Android Studio Hedgehog** (2023.1.1) ou superior
- **JDK 17**
- **SDK Android API 26+**
- Conexão com a internet (para baixar dependências Gradle)

### Passos

1. **Abrir o projeto**
   ```
   File → Open → selecione a pasta "BalancaOCR"
   ```

2. **Sincronizar Gradle**
   ```
   File → Sync Project with Gradle Files
   ```
   Aguarde o download de todas as dependências (~200 MB na primeira vez).

3. **Conectar dispositivo ou emulador**
   - Dispositivo físico: ative "Depuração USB" em Opções do desenvolvedor
   - Emulador: precisa ter câmera virtual configurada

4. **Build → Run (▶)**
   Ou para gerar APK:
   ```
   Build → Build Bundle(s) / APK(s) → Build APK(s)
   ```
   O APK estará em: `app/build/outputs/apk/debug/app-debug.apk`

---

## ⚙️ Configurações do WeightAnalyzer

Ajuste os parâmetros em `CameraActivity.kt` conforme sua balança:

```kotlin
private val analyzer = WeightAnalyzer(
    stabilityCount    = 6,      // leituras consecutivas p/ confirmar estabilidade
    stabilityThreshold = 0.02,  // variação máxima permitida (unidade da balança)
    minValidWeight    = 0.01,   // ignora valores abaixo disso (ex: balança vazia)
    minIntervalMs     = 2500L   // tempo mínimo entre dois registros (ms)
)
```

**Dicas de ajuste:**
- Balança com display piscante → aumente `stabilityCount` para 8–10
- Balança muito precisa (0.001g) → reduza `stabilityThreshold` para 0.005
- Partículas pesadas → `minValidWeight` pode ser 1.0 ou mais
- Pesagem rápida → reduza `minIntervalMs` para 1500

---

## 📱 Como usar

1. Abra o app → toque em **"+"** para criar uma nova sessão
2. Na câmera, **aponte para o display da balança**
3. Use o **retângulo pontilhado** como guia de enquadramento
4. A **barra verde** embaixo da câmera mostra o progresso de estabilidade
5. Quando o valor estabilizar → **flash verde** = partícula registrada automaticamente!
6. Troque a partícula → o ciclo se repete
7. Toque em **📊 Exportar** para salvar/compartilhar a planilha .xlsx

---

## 🛠️ Dependências principais

| Biblioteca | Versão | Uso |
|---|---|---|
| CameraX | 1.3.1 | Preview + análise de frames |
| ML Kit Text Recognition | 16.0.0 | OCR do display |
| Apache POI | 5.2.3 | Geração de .xlsx |
| Room | 2.6.1 | Banco de dados local |
| Material Components | 1.11.0 | UI |

---

## ⚠️ Observações importantes

- **Iluminação**: O OCR funciona melhor com boa iluminação. Evite reflexos no display.
- **Foco**: Mantenha o celular fixo (~15–25 cm do display). Um suporte é recomendado.
- **Display LCD vs LED**: Displays LED (7 segmentos) têm melhor reconhecimento.
- **Unidade**: O app detecta automaticamente g, kg, mg, lb, oz — se a balança não mostrar a unidade, assume "g".
- **Permissões**: Android 13+ pode pedir permissão de armazenamento na primeira exportação.

---

## 📊 Formato da planilha exportada

```
Sessão: Lote A
Exportado em: 15/03/2025 10:30:00
Total de partículas: 150

#  | Partícula | Massa   | Unidade | Data/Hora
1  | 1         | 0.425   | g       | 15/03/25 10:05:12
2  | 2         | 0.418   | g       | 15/03/25 10:05:28
...
                                              
Média:  0.421 g
Soma:   63.150 g
Mínimo: 0.398 g
Máximo: 0.447 g
```
