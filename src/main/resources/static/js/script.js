// DOM Elements
const connectionStatus = document.getElementById('connectionStatus');
const fpsCounter = document.getElementById('fpsCounter');
const responseTime = document.getElementById('responseTime');
const updateRateInput = document.getElementById('updateRateInput');
const updateRateDisplay = document.getElementById('updateRateDisplay');
const applyRateBtn = document.getElementById('applyRateBtn');
const detectBtn = document.getElementById('detectBtn');
const refreshBtn = document.getElementById('refreshBtn');
const detectSpinner = document.getElementById('detectSpinner');
const refreshSpinner = document.getElementById('refreshSpinner');
const variablesContainer = document.getElementById('variablesContainer');
const chartsContainer = document.getElementById('chartsContainer');
const statusMessage = document.getElementById('statusMessage');
const varCounter = document.getElementById('varCounter');
const addAllChartsBtn = document.getElementById('addAllChartsBtn');
const clearChartsBtn = document.getElementById('clearChartsBtn');
const performanceWarning = document.getElementById('performanceWarning');

// Variables para el control de actualización
let updateInterval = null;
let updateRate = 100; // Default: 100ms (10 veces/segundo)
let frameCount = 0;
let lastFpsUpdate = Date.now();
let lastUpdateTime = 0;
let useRequestAnimationFrame = false;

// Lista de variables detectadas
let detectedVariables = [];

// Mapeo de gráficas activas
let activeCharts = {};

// Colores para las gráficas
const chartColors = [
    '#4e79a7', '#f28e2c', '#e15759', '#76b7b2', '#59a14f',
    '#edc949', '#af7aa1', '#ff9da7', '#9c755f', '#bab0ab'
];

// Obtener un color según el índice
function getChartColor(index) {
    return chartColors[index % chartColors.length];
}

// Función para cargar variables desde el servidor
async function loadVariables() {
    try {
        refreshSpinner.style.display = 'inline-block';
        const response = await fetch('/api/variables');
        if (!response.ok) {
            throw new Error('Error al cargar variables');
        }

        const data = await response.json();
        detectedVariables = data.details || [];

        // Actualizar contador
        varCounter.textContent = detectedVariables.length;

        // Renderizar la lista de variables
        renderVariablesList();

        refreshSpinner.style.display = 'none';
        connectionStatus.classList.add('status-active');
        statusMessage.textContent = `Conectado con ${detectedVariables.length} variables`;
    } catch (error) {
        console.error('Error:', error);
        refreshSpinner.style.display = 'none';
        connectionStatus.classList.remove('status-active');
        statusMessage.textContent = `Error: ${error.message}`;
    }
}

// Renderizar lista de variables
function renderVariablesList() {
    variablesContainer.innerHTML = '';

    // Filtrar variables válidas: con respuesta OK y con un valor definido
    const validVariables = detectedVariables.filter(variable => 
        variable.responseCode === "OK" && 
        variable.lastValue !== null && 
        variable.lastValue !== undefined
    );

    if (validVariables.length === 0) {
        variablesContainer.innerHTML = `
            <div class="variable-item">
                <div class="variable-name">No se encontraron variables válidas</div>
            </div>
        `;
        return;
    }

    validVariables.forEach((variable, index) => {
        const valueType = typeof variable.lastValue;
        let formattedValue = variable.lastValue;
        let typeClass = '';

        // Formatear valor según el tipo de dato
        if (valueType === 'number') {
            if (variable.dataType === 'REAL' || variable.dataType.includes('REAL')) {
                formattedValue = parseFloat(variable.lastValue).toFixed(2);
                typeClass = 'type-float';
            } else {
                formattedValue = Math.round(variable.lastValue);
                typeClass = 'type-int';
            }
        } else if (valueType === 'boolean') {
            formattedValue = variable.lastValue ? 'TRUE' : 'FALSE';
            typeClass = 'type-bool';
        }

        // Crear elemento de variable
        const varItem = document.createElement('div');
        varItem.className = 'variable-item';
        varItem.dataset.name = variable.name;
        varItem.dataset.address = variable.address;
        varItem.dataset.type = variable.dataType;
        varItem.dataset.index = index;

        // Contenido del elemento
        varItem.innerHTML = `
            <div>
                <div class="variable-name">
                    ${variable.name} 
                    <span class="variable-type ${typeClass}">(${variable.dataType})</span>
                </div>
                <div class="variable-summary">
                    ${variable.min !== undefined ? `Min: ${variable.min.toFixed(1)} | Max: ${variable.max.toFixed(1)} | Prom: ${variable.getAverage ? variable.getAverage().toFixed(1) : ''}` : ''}
                </div>
            </div>
            <div class="variable-value ${typeClass}" id="value-${variable.name.replace(/\./g, '-')}">${formattedValue}</div>
        `;

        // Evento de clic para mostrar/ocultar gráfica
        varItem.addEventListener('click', function() {
            const varName = this.dataset.name;

            // Alternar selección
            if (this.classList.contains('selected')) {
                this.classList.remove('selected');
                // Eliminar gráfica si existe
                if (activeCharts[varName]) {
                    removeChart(varName);
                }
            } else {
                this.classList.add('selected');
                // Crear gráfica si no existe
                if (!activeCharts[varName]) {
                    createChart(variable, parseInt(this.dataset.index));
                }
            }
        });

        variablesContainer.appendChild(varItem);
    });
}

// Función para iniciar la detección de variables
async function detectVariables() {
    try {
        detectSpinner.style.display = 'inline-block';
        statusMessage.textContent = 'Detectando variables...';

        const response = await fetch('/api/detectVariables', {
            method: 'POST'
        });

        if (!response.ok) {
            throw new Error('Error al detectar variables');
        }

        const data = await response.json();

        // Cargar las variables actualizadas
        await loadVariables();

        detectSpinner.style.display = 'none';
        statusMessage.textContent = `Detección completa. Se encontraron ${data.count} variables.`;
    } catch (error) {
        console.error('Error:', error);
        detectSpinner.style.display = 'none';
        statusMessage.textContent = `Error en detección: ${error.message}`;
    }
}

// Crear una nueva gráfica para una variable
function createChart(variable, colorIndex) {
    const varName = variable.name;

    // Evitar duplicados
    if (activeCharts[varName]) {
        return;
    }

    // Eliminar el placeholder si existe
    const placeholder = document.querySelector('.chart-card.placeholder');
    if (placeholder) {
        placeholder.remove();
    }

    // Crear elemento de gráfica
    const chartCard = document.createElement('div');
    chartCard.className = 'chart-card';
    chartCard.id = `chart-card-${varName.replace(/\./g, '-')}`;

    // Definir el color de la gráfica
    const chartColor = getChartColor(colorIndex);

    // Encabezado de la gráfica
    chartCard.innerHTML = `
        <div class="chart-header">
            ${variable.name} (${variable.dataType})
            <div class="chart-controls">
                <button class="chart-control-btn toggle-btn" title="Reproducir/Pausar">
                    <span class="control-icon">⏸️</span>
                </button>
                <button class="chart-control-btn clear-btn" title="Limpiar datos">
                    <span class="control-icon">🗑️</span>
                </button>
                <button class="chart-control-btn close-btn" title="Cerrar gráfica">
                    <span>✕</span>
                </button>
            </div>
        </div>
        <div class="chart-body">
            <canvas id="chart-${varName.replace(/\./g, '-')}"></canvas>
        </div>
    `;

    // Agregar al contenedor
    chartsContainer.appendChild(chartCard);

    // Obtener el contexto del canvas
    const ctx = document.getElementById(`chart-${varName.replace(/\./g, '-')}`).getContext('2d');

    // Crear gráfica con Chart.js
    const maxDataPoints = 60; // Puntos a mostrar
    const chart = new Chart(ctx, {
        type: 'line',
        data: {
            labels: Array(maxDataPoints).fill('').map((_, i) => `-${(maxDataPoints - i) * (updateRate / 1000)}s`),
            datasets: [{
                label: variable.name,
                data: Array(maxDataPoints).fill(null),
                borderColor: chartColor,
                backgroundColor: `${chartColor}20`, // Color con transparencia
                borderWidth: 2,
                fill: true,
                tension: 0.3
            }]
        },
        options: {
            responsive: true,
            maintainAspectRatio: false,
            animation: false, // Mejor rendimiento
            scales: {
                x: {
                    grid: {
                        color: '#333333'
                    },
                    ticks: {
                        color: '#888888',
                        maxRotation: 0,
                        autoSkip: true,
                        maxTicksLimit: 6
                    }
                },
                y: {
                    grid: {
                        color: '#333333'
                    },
                    ticks: {
                        color: '#888888'
                    }
                }
            },
            plugins: {
                legend: {
                    display: false
                },
                tooltip: {
                    mode: 'index',
                    intersect: false,
                    backgroundColor: '#1e1e1e',
                    borderColor: '#404040',
                    borderWidth: 1,
                    titleColor: '#fff',
                    bodyColor: '#ddd',
                    padding: 10
                }
            }
        }
    });

    // Guardar referencia de la gráfica
    activeCharts[varName] = {
        chart: chart,
        element: chartCard,
        active: true, // Estado inicial: reproduciendo
        data: {
            min: typeof variable.min === 'number' ? variable.min : Infinity,
            max: typeof variable.max === 'number' ? variable.max : -Infinity,
            sum: 0,
            count: 0
        }
    };

    // Configurar botones de control
    const toggleBtn = chartCard.querySelector('.toggle-btn');
    const clearBtn = chartCard.querySelector('.clear-btn');
    const closeBtn = chartCard.querySelector('.close-btn');

    // Botón de reproducir/pausar
    toggleBtn.addEventListener('click', function(e) {
        e.stopPropagation();

        const chartInfo = activeCharts[varName];
        chartInfo.active = !chartInfo.active;

        const icon = this.querySelector('.control-icon');
        if (chartInfo.active) {
            icon.textContent = '⏸️';
            this.title = 'Pausar';
        } else {
            icon.textContent = '▶️';
            this.title = 'Reproducir';
        }
    });

    // Botón de limpiar datos
    clearBtn.addEventListener('click', function(e) {
        e.stopPropagation();

        const chartInfo = activeCharts[varName];
        const chartData = chartInfo.chart.data.datasets[0].data;

        // Limpiar datos
        chartData.fill(null);
        chartInfo.chart.update('none');

        // Resetear estadísticas
        chartInfo.data = {
            min: Infinity,
            max: -Infinity,
            sum: 0,
            count: 0
        };
    });

    // Botón de cerrar
    closeBtn.addEventListener('click', function(e) {
        e.stopPropagation();
        removeChart(varName);

        // Deseleccionar variable en la lista
        const varItem = document.querySelector(`.variable-item[data-name="${varName}"]`);
        if (varItem) {
            varItem.classList.remove('selected');
        }
    });

    return chart;
}

// Eliminar una gráfica
function removeChart(varName) {
    const chartInfo = activeCharts[varName];
    if (!chartInfo) return;

    // Destruir la instancia Chart.js
    chartInfo.chart.destroy();

    // Eliminar el elemento del DOM
    chartInfo.element.remove();

    // Eliminar de la lista de gráficas activas
    delete activeCharts[varName];

    // Si no quedan gráficas, mostrar el placeholder
    if (Object.keys(activeCharts).length === 0) {
        chartsContainer.innerHTML = `
            <div class="chart-card placeholder">
                <div class="chart-header">
                    Selecciona una variable para mostrar su gráfica
                </div>
                <div class="chart-body" style="display: flex; justify-content: center; align-items: center; color: #888;">
                    <p>Haz clic en una variable del panel izquierdo o usa el botón "Mostrar todas"</p>
                </div>
            </div>
        `;
    }
}

// Actualizar datos de una gráfica
function updateChartData(varName, value) {
    const chartInfo = activeCharts[varName];
    if (!chartInfo || !chartInfo.active) return;

    const chart = chartInfo.chart;
    const chartData = chart.data.datasets[0].data;

    // Agregar nuevo valor y eliminar el más antiguo
    chartData.push(value);
    chartData.shift();

    // Actualizar gráfica sin animación para mejor rendimiento
    chart.update('none');

    // Actualizar estadísticas
    if (typeof value === 'number') {
        chartInfo.data.min = Math.min(chartInfo.data.min, value);
        chartInfo.data.max = Math.max(chartInfo.data.max, value);
        chartInfo.data.sum += value;
        chartInfo.data.count++;
    }
}

// Mostrar todas las variables en gráficas
function showAllCharts() {
    // Limpia las gráficas existentes
    clearAllCharts();

    // Filtrar variables válidas
    const validVariables = detectedVariables.filter(variable => 
        variable.responseCode === "OK" && 
        variable.lastValue !== null && 
        variable.lastValue !== undefined
    );

     // Crear una gráfica para cada variable
     validVariables.forEach((variable, index) => {
        createChart(variable, index);

        // Seleccionar el elemento en la lista
        const varItem = document.querySelector(`.variable-item[data-name="${variable.name}"]`);
        if (varItem) {
            varItem.classList.add('selected');
        }
    });
}

// Limpiar todas las gráficas
function clearAllCharts() {
    // Destruir todas las gráficas activas
    Object.keys(activeCharts).forEach(varName => {
        const chartInfo = activeCharts[varName];
        chartInfo.chart.destroy();
        chartInfo.element.remove();

        // Deseleccionar en la lista
        const varItem = document.querySelector(`.variable-item[data-name="${varName}"]`);
        if (varItem) {
            varItem.classList.remove('selected');
        }
    });

    // Resetear la lista de gráficas activas
    activeCharts = {};

    // Agregar el placeholder
    chartsContainer.innerHTML = `
        <div class="chart-card placeholder">
            <div class="chart-header">
                Selecciona una variable para mostrar su gráfica
            </div>
            <div class="chart-body" style="display: flex; justify-content: center; align-items: center; color: #888;">
                <p>Haz clic en una variable del panel izquierdo o usa el botón "Mostrar todas"</p>
            </div>
        </div>
    `;
}

// Actualizar datos de todas las variables desde el servidor
async function updateVariablesData() {
    const startTime = performance.now();

    try {
        const response = await fetch('/api/variables');
        if (!response.ok) {
            throw new Error('Error al actualizar variables');
        }

        const data = await response.json();
        const variables = data.details || [];

        // Actualizar valores en la UI *solo si la variable es válida*
        variables.forEach(variable => {
          if (variable.responseCode === 'OK' && 
              variable.lastValue !== null && 
              variable.lastValue !== undefined) {
            const valueType = typeof variable.lastValue;
            let formattedValue = variable.lastValue;

            // Formatear según tipo
            if (valueType === 'number') {
                if (variable.dataType === 'REAL' || variable.dataType.includes('REAL')) {
                    formattedValue = parseFloat(variable.lastValue).toFixed(2);
                } else {
                    formattedValue = Math.round(variable.lastValue);
                }
            } else if (valueType === 'boolean') {
                formattedValue = variable.lastValue ? 'TRUE' : 'FALSE';
            }

            // Actualizar valor en la lista
            const valueElement = document.getElementById(`value-${variable.name.replace(/\./g, '-')}`);
            if (valueElement) {
                valueElement.textContent = formattedValue;
            }

            // Actualizar gráfica si existe
            if (activeCharts[variable.name]) {
                updateChartData(variable.name, variable.lastValue);
            }
          }
        });

        connectionStatus.classList.add('status-active');

        // Medir tiempo de respuesta
        const endTime = performance.now();
        responseTime.textContent = Math.round(endTime - startTime);

        // Actualizar contador de FPS
        frameCount++;
        const now = Date.now();
        if (now - lastFpsUpdate >= 1000) {
            fpsCounter.textContent = frameCount;
            frameCount = 0;
            lastFpsUpdate = now;
        }

    } catch (error) {
        console.error('Error:', error);
        connectionStatus.classList.remove('status-active');
    }
}

// Función para actualización con RequestAnimationFrame
function animationFrameUpdate() {
    const now = performance.now();
    const elapsed = now - lastUpdateTime;

    if (elapsed >= updateRate) {
        lastUpdateTime = now;
        updateVariablesData();
    }

    requestAnimationFrame(animationFrameUpdate);
}

// Función para actualización con setInterval
function startIntervalUpdate() {
    if (updateInterval) {
        clearInterval(updateInterval);
    }

    updateInterval = setInterval(() => {
        updateVariablesData();
    }, updateRate);
}

// Iniciar la actualización automática
function startAutoRefresh() {
    if (useRequestAnimationFrame) {
        lastUpdateTime = performance.now();
        requestAnimationFrame(animationFrameUpdate);
    } else {
        startIntervalUpdate();
    }
}

// Event listeners y inicialización
document.addEventListener('DOMContentLoaded', function() {
    // Conectar y cargar variables al iniciar
    loadVariables();

    // Iniciar actualización automática
    startAutoRefresh();

    // Detectar variables
    detectBtn.addEventListener('click', detectVariables);

    // Actualizar variables
    refreshBtn.addEventListener('click', loadVariables);

    // Cambiar tasa de actualización
    applyRateBtn.addEventListener('click', function() {
        const newRate = parseInt(updateRateInput.value);
        if (!isNaN(newRate) && newRate >= 1 && newRate <= 5000) {
            updateRate = newRate;
            updateRateDisplay.textContent = newRate;

            // Cambiar automáticamente a RequestAnimationFrame para tasas muy rápidas
            if (newRate < 16) { // 16ms es aproximadamente 60fps
                useRequestAnimationFrame = true;
                if (updateInterval) {
                    clearInterval(updateInterval);
                    updateInterval = null;
                }
                startAutoRefresh();
            } else {
                useRequestAnimationFrame = false;
                startAutoRefresh(); // Reinicia el intervalo.
            }

            // Mostrar advertencia para intervalos muy bajos
            if (newRate < 50) {
                performanceWarning.style.display = 'block';
            } else {
                performanceWarning.style.display = 'none';
            }
        } else {
            alert('Por favor, introduce un valor válido entre 1 y 5000 ms');
        }
    });

    // Mostrar todas las variables
    addAllChartsBtn.addEventListener('click', showAllCharts);

    // Limpiar todas las gráficas
    clearChartsBtn.addEventListener('click', clearAllCharts);

    // Advertencia de rendimiento al cambiar el intervalo
    updateRateInput.addEventListener('input', function () {
        const val = parseInt(this.value);
        if (!isNaN(val) && val >= 1 && val <= 5000) {
            // Mostrar advertencia si el intervalo es muy bajo
            if (val < 50) {
                performanceWarning.style.display = 'block';
            } else {
                performanceWarning.style.display = 'none';
            }
        } else {
            performanceWarning.style.display = 'none';
        }
    });
});