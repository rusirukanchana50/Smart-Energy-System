/**
 * server.js
 * ---------
 * HTTP server launcher for Ruhuna Smart Energy System Node.js backend.
 * Connects to MongoDB, bootstraps required campus buildings and user accounts,
 * starts background telemetry simulation, and listens on configured PORT.
 */

const path = require('path');
require('dotenv').config({ path: path.join(__dirname, '..', '.env') });
const app = require('./app');
const { connectDB } = require('./config/db');
const User = require('./models/User');
const EnergyReading = require('./models/EnergyReading');
const { runSimulationCycle } = require('./simulator');
const { initBuildings } = require('./init_db');

const PORT = process.env.PORT || 5000;
let simulatorIntervalId = null;

/**
 * Ensures required administrative and viewer accounts exist on server startup.
 * Non-destructive: preserves existing database records and any other user accounts.
 */
async function ensureDefaultUsers() {
  try {
    // 1. Ensure administrator account exists and password hash is valid
    const adminEmail = 'admin@eng.ruh.ac.lk';
    const adminPassword = process.env.ADMIN_INITIAL_PASSWORD || 'Admin@1234';
    let admin = await User.findOne({ email: adminEmail });

    if (!admin) {
      admin = await User.create({
        email: adminEmail,
        full_name: 'System Administrator',
        role: 'Administrator',
        password: adminPassword
      });
      console.log(`[Init] Created default administrator: ${adminEmail}`);
    } else {
      admin.full_name = 'System Administrator';
      admin.role = 'Administrator';
      admin.password = adminPassword; // Pre-save hook triggers bcrypt hashing properly
      await admin.save();
      console.log(`[Init] Verified and updated administrator: ${adminEmail}`);
    }

    // 2. Ensure viewer account exists
    const viewerEmail = 'viewer@eng.ruh.ac.lk';
    const viewerPassword = process.env.VIEWER_INITIAL_PASSWORD || 'Viewer@1234';
    const viewer = await User.findOne({ email: viewerEmail });

    if (!viewer) {
      await User.create({
        email: viewerEmail,
        full_name: 'Faculty Viewer',
        role: 'Viewer',
        password: viewerPassword
      });
      console.log(`[Init] Created default viewer: ${viewerEmail}`);
    }
  } catch (err) {
    console.error('[Init] Error ensuring default users:', err.message);
  }
}

/**
 * Initializes and manages background energy telemetry simulation inside Express.
 * Seeds initial baseline readings if none exist, then executes on configured interval.
 */
async function startTelemetrySimulator() {
  if (process.env.NODE_ENV === 'test' || process.env.SIMULATOR_ENABLED === 'false') {
    return;
  }

  const intervalSeconds = parseFloat(process.env.SIMULATOR_INTERVAL || '15.0');
  console.log(`[Simulator] Starting background telemetry simulation (interval: ${intervalSeconds}s)...`);

  try {
    const readingCount = await EnergyReading.countDocuments();
    if (readingCount === 0) {
      console.log('[Simulator] No energy readings detected in database. Seeding initial baseline telemetry...');
      await runSimulationCycle({ verbose: true });
      console.log('[Simulator] Initial baseline telemetry seeded successfully.');
    } else {
      // Generate an immediate fresh cycle on boot so live dashboard displays telemetry right away
      await runSimulationCycle({ verbose: false });
      console.log('[Simulator] Fresh startup telemetry cycle generated.');
    }
  } catch (err) {
    console.error('[Simulator] Error initializing startup telemetry readings:', err.message);
  }

  // Continuous background simulation loop
  simulatorIntervalId = setInterval(async () => {
    try {
      await runSimulationCycle({ verbose: false });
    } catch (err) {
      console.error('[Simulator] Error in simulation cycle:', err.message);
    }
  }, intervalSeconds * 1000);

  // Unref interval timer so it does not block node process shutdown
  if (simulatorIntervalId && typeof simulatorIntervalId.unref === 'function') {
    simulatorIntervalId.unref();
  }
}

async function startServer() {
  try {
    await connectDB();
    await initBuildings({ silent: true });
    await ensureDefaultUsers();
    await startTelemetrySimulator();

    const server = app.listen(PORT, () => {
      console.log(`[Express] Server running on http://localhost:${PORT}`);
      console.log(`[Express] Health check at http://localhost:${PORT}/api/health`);
    });

    // Graceful shutdown handling
    const handleShutdown = async (signal) => {
      console.log(`\n[Server] Received ${signal}. Shutting down gracefully...`);
      if (simulatorIntervalId) {
        clearInterval(simulatorIntervalId);
        simulatorIntervalId = null;
      }
      server.close(async () => {
        const { disconnectDB } = require('./config/db');
        await disconnectDB();
        console.log('[Server] Graceful shutdown complete.');
        process.exit(0);
      });
    };

    process.on('SIGINT', () => handleShutdown('SIGINT'));
    process.on('SIGTERM', () => handleShutdown('SIGTERM'));

    return server;
  } catch (err) {
    console.error('[Server] Failed to start server:', err);
    process.exit(1);
  }
}

if (require.main === module) {
  startServer();
}

module.exports = { startServer, ensureDefaultUsers, startTelemetrySimulator, initBuildings };
