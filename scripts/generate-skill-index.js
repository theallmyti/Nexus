/**
 * generate-skill-index.js
 * Scans all Antigravity/Gemini CLI skill directories and compiles a centralized,
 * token-efficient index into D:\Random project\Memory\skill-list.md with:
 *   Tier 1: Daily Quick-Picks (~40 common skills)
 *   Tier 2: Full Categorized & Alphabetical Index (500+ skills in 1-line table rows)
 *   Tier 3: Negative Search Ledger (Preserved across runs)
 */

const fs = require('fs');
const path = require('path');

const OUTPUT_FILE = 'D:/Random project/Memory/skill-list.md';

const SEARCH_DIRS = [
  'C:/Users/shilp/.gemini/config/skills',
  'C:/Users/shilp/.gemini/skills',
  'C:/Users/shilp/.gemini/config/plugins',
  'C:/Users/shilp/.gemini/antigravity-cli/builtin/skills',
  'C:/Users/shilp/.agents/skills'
];

// Helper to escape markdown table pipes
function cleanTableText(text) {
  if (!text) return '';
  return text.replace(/\|/g, '\\|').replace(/\r?\n/g, ' ').replace(/\s+/g, ' ').trim();
}

// Parse YAML frontmatter
function parseSkillFile(filePath) {
  try {
    const content = fs.readFileSync(filePath, 'utf8');
    const match = content.match(/^---\r?\n([\s\S]*?)\r?\n---/);
    if (!match) {
      const folderName = path.basename(path.dirname(filePath));
      return { name: folderName, description: 'No description provided.', path: filePath };
    }
    const yaml = match[1];
    const nameMatch = yaml.match(/^name:\s*(.+)$/m);
    
    // Match description block (supporting |, >, |-, >-, |+, >+)
    const descMatch = yaml.match(/^description:\s*(?:>[-+]?|\|[-+]?)?\s*\r?\n?([\s\S]*?)(?=\r?\n[a-zA-Z0-9_-]+:\s*|$)/m);

    const folderName = path.basename(path.dirname(filePath));
    const name = nameMatch ? nameMatch[1].trim().replace(/^['"]|['"]$/g, '') : folderName;
    let description = descMatch ? descMatch[1].trim() : 'No description provided.';
    
    // If it still starts with a stray block indicator, clean it up
    description = description.replace(/^[|>][-+]?\s*/, '');
    description = cleanTableText(description);

    return { name, description, path: filePath.replace(/\\/g, '/') };
  } catch (err) {
    return null;
  }
}

// Recursively find all SKILL.md
function findSkills(dir, results = []) {
  if (!fs.existsSync(dir)) return results;
  try {
    const entries = fs.readdirSync(dir, { withFileTypes: true });
    for (const entry of entries) {
      const full = path.join(dir, entry.name);
      if (entry.isDirectory()) {
        const skillMd = path.join(full, 'SKILL.md');
        if (fs.existsSync(skillMd)) {
          results.push(skillMd);
        } else {
          findSkills(full, results);
        }
      }
    }
  } catch (e) {}
  return results;
}

// Categorize skills by keywords
function categorize(name, desc) {
  const text = (name + ' ' + desc).toLowerCase();
  
  if (text.includes('security') || text.includes('vulnerab') || text.includes('data-loss') || text.includes('auth') || text.includes('csrf') || text.includes('xss') || text.includes('hipaa') || text.includes('secret')) {
    return 'Security & Audit';
  }
  if (text.includes('test') || text.includes('tdd') || text.includes('e2e') || text.includes('jest') || text.includes('vitest') || text.includes('pytest') || text.includes('mock') || text.includes('debug') || text.includes('diagnos') || text.includes('defect')) {
    return 'Testing & Quality';
  }
  if (text.includes('architect') || text.includes('domain-model') || text.includes('system design') || text.includes('rfc') || text.includes('deep module') || text.includes('codebase-design') || text.includes('decision')) {
    return 'Architecture & Planning';
  }
  if (text.includes('ui') || text.includes('frontend') || text.includes('css') || text.includes('styling') || text.includes('react') || text.includes('tailwind') || text.includes('vue') || text.includes('svelte') || text.includes('component') || text.includes('landing page') || text.includes('hallmark') || text.includes('unslop') || text.includes('impeccable')) {
    return 'UI & Frontend';
  }
  if (text.includes('docker') || text.includes('k8s') || text.includes('kubernetes') || text.includes('deploy') || text.includes('ci/cd') || text.includes('cisco') || text.includes('network') || text.includes('homelab') || text.includes('linux') || text.includes('wireguard')) {
    return 'DevOps, Infra & Network';
  }
  if (text.includes('backend') || text.includes('api') || text.includes('database') || text.includes('postgres') || text.includes('mysql') || text.includes('sql') || text.includes('orm') || text.includes('prisma') || text.includes('redis') || text.includes('rest') || text.includes('express') || text.includes('django') || text.includes('springboot')) {
    return 'Backend, APIs & DB';
  }
  if (text.includes('journal') || text.includes('git') || text.includes('changelog') || text.includes('skill') || text.includes('agent') || text.includes('memory') || text.includes('workflow') || text.includes('customization')) {
    return 'Agent Ops & Meta';
  }
  return 'Data, Science & Specialized';
}

// Extract existing Negative Search Ledger from file if it exists
function extractExistingLedger(filePath) {
  if (!fs.existsSync(filePath)) return null;
  const content = fs.readFileSync(filePath, 'utf8');
  const marker = '## 3. Negative Search Ledger (Searched & Not Found)';
  const endMarker = '## 4. How to Install Missing Skills';
  const idx = content.indexOf(marker);
  if (idx !== -1) {
    const afterMarker = content.substring(idx + marker.length);
    const endIdx = afterMarker.indexOf(endMarker);
    const section = endIdx !== -1 ? afterMarker.substring(0, endIdx) : afterMarker;
    // Strip header protocols if already in the section
    const tableOnly = section.replace(/---\s*$/, '').trim();
    return tableOnly;
  }
  return null;
}

function main() {
  console.log('Scanning skill directories...');
  const skillFiles = [];
  for (const dir of SEARCH_DIRS) {
    findSkills(dir, skillFiles);
  }

  const skillMap = new Map();
  for (const file of skillFiles) {
    const parsed = parseSkillFile(file);
    if (parsed && !skillMap.has(parsed.name)) {
      skillMap.set(parsed.name, parsed);
    }
  }

  const skills = Array.from(skillMap.values()).sort((a, b) => a.name.localeCompare(b.name));
  console.log(`Found ${skills.length} unique skills.`);

  // Tier 1: Curated Daily Quick-Picks
  const quickPicks = [
    { cat: 'UI & Polish', name: 'hallmark', when: 'Clean, modern, premium UI / landing page design' },
    { cat: 'UI & Polish', name: 'impeccable', when: 'Fixing design flaws, visual bugs, aesthetic polish' },
    { cat: 'UI & Polish', name: 'unslop', when: 'Stripping generic AI frontend cliches, clean styling' },
    { cat: 'UI & Polish', name: 'frontend-design-direction', when: 'Product design direction, component styling' },
    { cat: 'Architecture', name: 'agent-architecture-audit', when: 'Full-stack agent diagnostics & architecture review' },
    { cat: 'Architecture', name: 'domain-modeling', when: 'Domain boundaries, data models, entity relationships' },
    { cat: 'Architecture', name: 'codebase-design', when: 'Deep module interfaces, information hiding' },
    { cat: 'Architecture', name: 'grilling', when: 'Stress-testing ideas, architecture decisions, trade-offs' },
    { cat: 'Testing & QA', name: 'tdd', when: 'Test-driven development (red-green-refactor)' },
    { cat: 'Testing & QA', name: 'diagnosing-bugs', when: 'Root-cause diagnosis for tricky bugs and regressions' },
    { cat: 'Testing & QA', name: 'e2e-testing', when: 'Playwright end-to-end test suites' },
    { cat: 'Testing & QA', name: 'python-testing', when: 'Pytest test suites, mocks, fixtures' },
    { cat: 'Testing & QA', name: 'react-testing', when: 'React component tests (Vitest / RTL / MSW)' },
    { cat: 'Security', name: 'security-audit', when: 'Full vulnerability scan, authz, secret leak checks' },
    { cat: 'Security', name: 'accidental-data-loss-prevention', when: 'Destructive ops guardrails, migration safeguards' },
    { cat: 'DevOps & Infra', name: 'deployment-patterns', when: 'Docker, CI/CD pipelines, production readiness' },
    { cat: 'DevOps & Infra', name: 'homelab-network-setup', when: 'Home server network, gateway, DNS, subnets' },
    { cat: 'Backend & DB', name: 'backend-patterns', when: 'Node.js, Express, Next.js API architecture' },
    { cat: 'Backend & DB', name: 'postgres-patterns', when: 'PostgreSQL schema, indexing, query optimization' },
    { cat: 'Agent Ops', name: 'journal', when: 'Log session to Obsidian vault (Log.md / Overview.md)' },
    { cat: 'Agent Ops', name: 'changelog-generator', when: 'Auto-generate customer release notes from commits' },
    { cat: 'Agent Ops', name: 'skill-scout', when: 'Search and find new skills on web / GitHub' },
    { cat: 'Agent Ops', name: 'agy-customizations', when: 'Antigravity CLI rules, hooks, skills customization' },
    { cat: 'Diagrams', name: 'anatomy', when: 'System architecture drawings, technical diagrams' },
    { cat: 'Diagrams', name: 'graphify', when: 'Knowledge graph generation and visual clustering' }
  ];

  let md = `# Antigravity Skills Master Index & Discovery Ledger\n\n`;
  md += `> **Last Synchronized:** ${new Date().toISOString().split('T')[0]}\n`;
  md += `> **Total Indexed Skills:** ${skills.length}\n`;
  md += `> **Location:** Synced Obsidian Vault (\`D:\\Random project\\Memory\\skill-list.md\`)\n\n`;

  md += `## 1. Quick-Picks (Top Essential Skills)\n\n`;
  md += `> Consult this table first for 90% of development workflows.\n\n`;
  md += `| Category | Skill | When to Use | Path |\n`;
  md += `| :--- | :--- | :--- | :--- |\n`;

  for (const q of quickPicks) {
    const match = skills.find(s => s.name === q.name);
    const p = match ? match.path : `(Search via skill-scout)`;
    md += `| **${q.cat}** | \`${q.name}\` | ${q.when} | \`${p}\` |\n`;
  }

  md += `\n---\n\n`;
  md += `## 2. Complete Skills Catalog (${skills.length} Installed)\n\n`;
  md += `| Skill | Category | Description / Purpose | File Path |\n`;
  md += `| :--- | :--- | :--- | :--- |\n`;

  for (const s of skills) {
    const cat = categorize(s.name, s.description);
    // Truncate long descriptions to keep table readable
    let desc = s.description;
    if (desc.length > 140) {
      desc = desc.substring(0, 137) + '...';
    }
    md += `| \`${s.name}\` | ${cat} | ${desc} | \`${s.path}\` |\n`;
  }

  md += `\n---\n\n`;
  md += `## 3. Negative Search Ledger (Searched & Not Found)\n\n`;

  const existingLedger = extractExistingLedger(OUTPUT_FILE);
  if (existingLedger) {
    md += existingLedger + '\n\n';
  } else {
    md += `> **Protocol:** Check this table before running any external skill search. If a capability was already searched and verified unavailable, **do not re-search**; proceed using standard engineering.\n\n`;
    md += `| Query / Capability | Date Checked | Search Tool | Result / Guidance |\n`;
    md += `| :--- | :--- | :--- | :--- |\n`;
    md += `| \`find-skill\` | 2026-11-11 | Local filesystem | Not a skill. Use \`skill-scout\` or \`npx skills find <query>\` |\n\n`;
  }

  md += `---\n\n`;
  md += `## 4. How to Install Missing Skills\n\n`;
  md += `If a skill is truly needed and not in the catalog above:\n`;
  md += `1. **Search**: \`npx skills find "<query>"\` (or \`gh search repos "skill <query>"\`)\n`;
  md += `2. **Install (Primary)**: \`npx skills add <source> --skill <skill-name>\`\n`;
  md += `3. **Install (Fallback)**: \`git clone <repo-url> "C:\\Users\\shilp\\.gemini\\skills\\<skill-name>"\`\n`;
  md += `4. **Update Ledger**: Append new skill to this file or run \`node "D:\\Random project\\MS\\scripts\\generate-skill-index.js"\`.\n`;

  // Ensure directory exists
  const outDir = path.dirname(OUTPUT_FILE);
  if (!fs.existsSync(outDir)) {
    fs.mkdirSync(outDir, { recursive: true });
  }

  fs.writeFileSync(OUTPUT_FILE, md, 'utf8');
  console.log(`Successfully generated master index at: ${OUTPUT_FILE}`);
}

main();
