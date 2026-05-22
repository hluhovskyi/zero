import { google } from 'googleapis';
import { Octokit } from '@octokit/rest';

const PACKAGE_NAME = process.env.PACKAGE_NAME;
const REPO_OWNER = process.env.REPO_OWNER;
const REPO_NAME = process.env.REPO_NAME;
const GITHUB_TOKEN = process.env.GITHUB_TOKEN;

const auth = new google.auth.GoogleAuth({
    scopes: ['https://www.googleapis.com/auth/playintegrity'],
});
const playintegrity = google.playintegrity({ version: 'v1', auth });
const octokit = new Octokit({ auth: GITHUB_TOKEN });

export const feedback = async (req, res) => {
    if (req.method !== 'POST') {
        res.status(405).json({ error: 'method not allowed' });
        return;
    }

    const token = req.get('X-Integrity-Token');
    if (!token) {
        res.status(401).json({ error: 'missing token' });
        return;
    }

    let verdict;
    try {
        const decoded = await playintegrity.v1.decodeIntegrityToken({
            packageName: PACKAGE_NAME,
            requestBody: { integrityToken: token },
        });
        verdict = decoded.data.tokenPayloadExternal;
    } catch (e) {
        res.status(401).json({ error: 'integrity decode failed' });
        return;
    }

    const appOk = verdict.appIntegrity?.appRecognitionVerdict === 'PLAY_RECOGNIZED';
    const deviceOk = verdict.deviceIntegrity?.deviceRecognitionVerdict?.includes('MEETS_DEVICE_INTEGRITY');
    const pkgOk = verdict.appIntegrity?.packageName === PACKAGE_NAME;
    if (!appOk || !deviceOk || !pkgOk) {
        res.status(403).json({ error: 'integrity verdict rejected' });
        return;
    }

    const { title, body, labels = [] } = req.body || {};
    if (typeof title !== 'string' || typeof body !== 'string' || !title || !body) {
        res.status(400).json({ error: 'invalid payload' });
        return;
    }

    const ALLOWED_LABELS = new Set(['feedback', 'debug', 'bug', 'idea', 'other']);
    const safeLabels = Array.isArray(labels)
        ? labels.filter((l) => typeof l === 'string' && ALLOWED_LABELS.has(l)).slice(0, 5)
        : [];

    try {
        const issue = await octokit.issues.create({
            owner: REPO_OWNER,
            repo: REPO_NAME,
            title,
            body,
            labels: safeLabels,
        });
        res.status(201).json({ issueUrl: issue.data.html_url });
    } catch (e) {
        res.status(502).json({ error: 'github call failed' });
    }
};
