const nodemailer = require('nodemailer');

let cachedTransporter;

function getTransporter() {
  if (cachedTransporter) return cachedTransporter;
  const { SMTP_HOST, SMTP_PORT, SMTP_USER, SMTP_PASS, SMTP_SECURE } = process.env;
  if (!SMTP_HOST || !SMTP_PORT || !SMTP_USER || !SMTP_PASS) {
    throw new Error('SMTP environment variables are not configured');
  }
  cachedTransporter = nodemailer.createTransport({
    host: SMTP_HOST,
    port: Number(SMTP_PORT),
    secure: String(SMTP_SECURE || 'false') === 'true',
    auth: { user: SMTP_USER, pass: SMTP_PASS }
  });
  return cachedTransporter;
}

async function sendMail(to, subject, html) {
  const transporter = getTransporter();
  const from = process.env.SMTP_FROM || process.env.SMTP_USER;
  await transporter.sendMail({ from, to, subject, html });
}

module.exports = { sendMail };


