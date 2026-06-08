INSERT INTO tests (name, description, price, turnaround_hours, result_format, created_by)
VALUES
    ('Full Blood Count', 'Routine blood panel',                12000.00, 24,  'numeric',
        (SELECT id FROM users WHERE email = 'admin@sante.test')),
    ('Chest X-Ray',      'Diagnostic chest imaging',           18000.00, 6,   'image',
        (SELECT id FROM users WHERE email = 'admin@sante.test')),
    ('Tissue Biopsy',    'Histopathology report (PDF output)', 45000.00, 120, 'pdf',
        (SELECT id FROM users WHERE email = 'admin@sante.test')),
    -- Hematology Tests
    ('Complete Blood Count (CBC)', 'Measures red/white blood cells, hemoglobin, hematocrit, platelets', 15000.00, 24, 'numeric',
        (SELECT id FROM users WHERE email = 'admin@sante.test')),
    ('WBC Differential', 'Percentage of different white blood cell types (neutrophils, lymphocytes, monocytes, eosinophils, basophils)', 8000.00, 24, 'numeric',
        (SELECT id FROM users WHERE email = 'admin@sante.test')),
    ('Erythrocyte Sedimentation Rate (ESR)', 'Measures inflammation markers in blood', 5000.00, 8, 'numeric',
        (SELECT id FROM users WHERE email = 'admin@sante.test')),
    
    -- Chemistry / Metabolic Tests
    ('Liver Function Test (LFT)', 'Measures ALT, AST, ALP, GGT, bilirubin, albumin, total protein', 18000.00, 36, 'numeric',
        (SELECT id FROM users WHERE email = 'admin@sante.test')),
    ('Renal Function Test (RFT)', 'Measures creatinine, BUN, uric acid, electrolytes (Na, K, Cl)', 15000.00, 24, 'numeric',
        (SELECT id FROM users WHERE email = 'admin@sante.test')),
    ('Lipid Profile', 'Measures total cholesterol, HDL, LDL, triglycerides', 12000.00, 24, 'numeric',
        (SELECT id FROM users WHERE email = 'admin@sante.test')),
    ('Fasting Blood Sugar (FBS)', 'Measures blood glucose after 8-12 hours fasting', 4000.00, 6, 'numeric',
        (SELECT id FROM users WHERE email = 'admin@sante.test')),
    ('HbA1c (Glycated Hemoglobin)', 'Measures average blood sugar over 2-3 months', 10000.00, 24, 'numeric',
        (SELECT id FROM users WHERE email = 'admin@sante.test')),
    
    -- Hormone Tests
    ('Thyroid Function Test (TFT)', 'Measures TSH, T3, T4 levels', 22000.00, 36, 'numeric',
        (SELECT id FROM users WHERE email = 'admin@sante.test')),
    ('Pregnancy Test (Serum beta-hCG)', 'Quantitative hCG for pregnancy detection', 8000.00, 4, 'numeric',
        (SELECT id FROM users WHERE email = 'admin@sante.test')),
    ('Testosterone Total', 'Measures total testosterone levels in blood', 18000.00, 48, 'numeric',
        (SELECT id FROM users WHERE email = 'admin@sante.test')),
    
    -- Microbiology / Infectious Diseases
    ('Urine Culture & Sensitivity', 'Identifies bacterial infection and antibiotic sensitivity', 15000.00, 72, 'pdf',
        (SELECT id FROM users WHERE email = 'admin@sante.test')),
    ('Blood Culture', 'Detects bacterial/fungal infection in bloodstream', 25000.00, 120, 'pdf',
        (SELECT id FROM users WHERE email = 'admin@sante.test')),
    ('Malaria Microscopy (Blood Film)', 'Microscopic examination for malaria parasites', 5000.00, 4, 'text',
        (SELECT id FROM users WHERE email = 'admin@sante.test')),
    ('Widal Test', 'Detects typhoid fever antibodies', 7000.00, 24, 'numeric',
        (SELECT id FROM users WHERE email = 'admin@sante.test')),
    ('Hepatitis B Surface Antigen (HBsAg)', 'Screening for Hepatitis B infection', 12000.00, 24, 'text',
        (SELECT id FROM users WHERE email = 'admin@sante.test')),
    ('Hepatitis C Antibody (Anti-HCV)', 'Screening for Hepatitis C infection', 14000.00, 24, 'text',
        (SELECT id FROM users WHERE email = 'admin@sante.test')),
    ('HIV Screening (Rapid Test)', 'HIV 1 & 2 antibody test', 8000.00, 2, 'text',
        (SELECT id FROM users WHERE email = 'admin@sante.test')),
    
    -- Radiology / Imaging
    ('Abdominal Ultrasound', 'Examination of abdominal organs (liver, gallbladder, kidneys, pancreas, spleen)', 25000.00, 24, 'image',
        (SELECT id FROM users WHERE email = 'admin@sante.test')),
    ('Pelvic Ultrasound', 'Examination of pelvic organs (uterus, ovaries, bladder)', 25000.00, 24, 'image',
        (SELECT id FROM users WHERE email = 'admin@sante.test')),
    ('Doppler Ultrasound', 'Evaluates blood flow in arteries and veins', 35000.00, 24, 'image',
        (SELECT id FROM users WHERE email = 'admin@sante.test')),
    ('CT Scan (Plain)', 'Computed tomography without contrast', 85000.00, 48, 'image',
        (SELECT id FROM users WHERE email = 'admin@sante.test')),
    ('CT Scan (With Contrast)', 'Computed tomography with IV contrast dye', 120000.00, 48, 'image',
        (SELECT id FROM users WHERE email = 'admin@sante.test')),
    ('MRI (Plain)', 'Magnetic resonance imaging without contrast', 150000.00, 72, 'image',
        (SELECT id FROM users WHERE email = 'admin@sante.test')),
    ('Mammogram', 'Breast cancer screening imaging', 35000.00, 24, 'image',
        (SELECT id FROM users WHERE email = 'admin@sante.test')),
    ('Bone Density Scan (DEXA)', 'Measures bone mineral density for osteoporosis screening', 40000.00, 24, 'numeric',
        (SELECT id FROM users WHERE email = 'admin@sante.test')),
    
    -- Urinalysis
    ('Urinalysis (Dipstick)', 'Routine urine examination for glucose, protein, blood, pH, specific gravity', 5000.00, 4, 'numeric',
        (SELECT id FROM users WHERE email = 'admin@sante.test')),
    ('24-Hour Urine Protein', 'Quantifies protein excretion over 24 hours', 12000.00, 72, 'numeric',
        (SELECT id FROM users WHERE email = 'admin@sante.test')),
    
    -- Cardiac Markers
    ('Troponin I', 'Cardiac marker for heart attack detection', 18000.00, 6, 'numeric',
        (SELECT id FROM users WHERE email = 'admin@sante.test')),
    ('CK-MB', 'Creatine kinase-MB for cardiac muscle damage', 12000.00, 6, 'numeric',
        (SELECT id FROM users WHERE email = 'admin@sante.test')),
    
    -- Coagulation
    ('Prothrombin Time (PT/INR)', 'Measures blood clotting time', 8000.00, 12, 'numeric',
        (SELECT id FROM users WHERE email = 'admin@sante.test')),
    ('Activated Partial Thromboplastin Time (APTT)', 'Measures intrinsic coagulation pathway', 8000.00, 12, 'numeric',
        (SELECT id FROM users WHERE email = 'admin@sante.test')),
    
    -- Genetic / Molecular Tests
    ('COVID-19 PCR Test', 'RT-PCR detection of SARS-CoV-2 virus', 35000.00, 48, 'text',
        (SELECT id FROM users WHERE email = 'admin@sante.test')),
    ('COVID-19 Antigen Rapid Test', 'Rapid antigen detection for SARS-CoV-2', 15000.00, 1, 'text',
        (SELECT id FROM users WHERE email = 'admin@sante.test')),
    
    -- Pathology / Histology
    ('Pap Smear', 'Cervical cancer screening', 18000.00, 72, 'pdf',
        (SELECT id FROM users WHERE email = 'admin@sante.test')),
    ('Sputum AFB (TB Test)', 'Acid-fast bacilli smear for tuberculosis', 10000.00, 48, 'text',
        (SELECT id FROM users WHERE email = 'admin@sante.test')),
    
    -- Drug Levels / Toxicology
    ('Paracetamol Level', 'Measures acetaminophen concentration in blood', 10000.00, 12, 'numeric',
        (SELECT id FROM users WHERE email = 'admin@sante.test')),
    ('Alcohol Blood Level', 'Quantitative blood alcohol concentration', 12000.00, 8, 'numeric',
        (SELECT id FROM users WHERE email = 'admin@sante.test')),
    
    -- Vitamins / Nutrition
    ('Vitamin B12', 'Measures cobalamin levels', 15000.00, 48, 'numeric',
        (SELECT id FROM users WHERE email = 'admin@sante.test')),
    ('Vitamin D (25-Hydroxy)', 'Measures vitamin D status', 20000.00, 72, 'numeric',
        (SELECT id FROM users WHERE email = 'admin@sante.test')),
    ('Ferritin', 'Measures iron storage protein', 12000.00, 24, 'numeric',
        (SELECT id FROM users WHERE email = 'admin@sante.test')),
    
    -- Tumor Markers
    ('CA-125', 'Ovarian cancer tumor marker', 22000.00, 48, 'numeric',
        (SELECT id FROM users WHERE email = 'admin@sante.test')),
    ('PSA (Prostate Specific Antigen)', 'Prostate cancer screening marker', 18000.00, 48, 'numeric',
        (SELECT id FROM users WHERE email = 'admin@sante.test')),
    ('CEA (Carcinoembryonic Antigen)', 'Colorectal cancer tumor marker', 20000.00, 48, 'numeric',
        (SELECT id FROM users WHERE email = 'admin@sante.test'));

-- Update 8 tests to be unavailable
UPDATE tests SET is_active = FALSE WHERE name = 'Renal Function Test (RFT)';
UPDATE tests SET is_active = FALSE WHERE name = 'Thyroid Function Test (TFT)';
UPDATE tests SET is_active = FALSE WHERE name = 'Urine Culture & Sensitivity';
UPDATE tests SET is_active = FALSE WHERE name = 'Widal Test';
UPDATE tests SET is_active = FALSE WHERE name = 'Pelvic Ultrasound';
UPDATE tests SET is_active = FALSE WHERE name = 'CT Scan (With Contrast)';
UPDATE tests SET is_active = FALSE WHERE name = 'Bone Density Scan (DEXA)';
UPDATE tests SET is_active = FALSE WHERE name = 'Tissue Biopsy';