/**
 * api/index.js
 * ------------
 * Vercel Serverless Function entry point for Ruhuna Smart Energy System.
 * Connects to MongoDB Atlas via connection-cached connectDB() before delegating
 * incoming requests to the Express application.
 */

const path = require('path');
const app = require('../node_backend/app');
const { connectDB } = require('../node_backend/config/db');

module.exports = async (req, res) => {
  try {
    // Ensure MongoDB connection is established / reused
    await connectDB();
    // Pass request to Express application
    return app(req, res);
  } catch (err) {
    console.error('[Vercel Serverless Entrypoint] Database connection error:', err);
    res.status(500).json({
      status: 'error',
      message: 'Failed to connect to database'
    });
  }
};
